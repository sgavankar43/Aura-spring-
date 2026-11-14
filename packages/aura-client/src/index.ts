/**
 * Official TypeScript Client SDK for Aura Distributed Feature Flag Platform
 */

export interface EvaluationContext {
  userId?: string | null;
  email?: string | null;
  attributes?: Record<string, unknown>;
}

export interface AuraOptions {
  apiKey: string;
  endpoint?: string;
  streamEndpoint?: string;
  environment?: string;
  pollIntervalMs?: number;
  enableStream?: boolean;
  context?: EvaluationContext;
  defaultFlags?: Record<string, boolean>;
}

export interface FlagStateChange {
  from: boolean;
  to: boolean;
}

export interface AuraUpdateEvent {
  flags: Record<string, boolean>;
  changedFlags: Record<string, FlagStateChange>;
  environment: string;
  context: EvaluationContext;
  connectionMode: 'stream' | 'polling' | 'offline';
  evaluatedAt: string | null;
  isInitial: boolean;
}

export type AuraListener = (event: AuraUpdateEvent) => void;

export class AuraClient {
  private endpoint: string;
  private streamEndpoint: string;
  private apiKey: string;
  public environment: string;
  private pollIntervalMs: number;
  public context: EvaluationContext;
  public flags: Record<string, boolean>;
  private listeners: Set<AuraListener>;
  private timer: ReturnType<typeof setInterval> | null;
  private eventSource: EventSource | null;
  public connectionMode: 'stream' | 'polling' | 'offline';
  public lastEvaluatedAt: string | null;
  public lastError: Error | null;

  constructor(options: AuraOptions) {
    if (!options.apiKey) {
      throw new Error('[AuraSDK] apiKey is required to initialize AuraClient');
    }

    this.apiKey = options.apiKey;
    this.endpoint = options.endpoint || 'https://aura-spring.onrender.com/api/v1/evaluate';
    this.streamEndpoint = options.streamEndpoint || this.endpoint.replace('/evaluate', '/stream');
    this.environment = options.environment || 'development';
    this.pollIntervalMs = options.pollIntervalMs || 2500;
    this.context = options.context || {};
    this.flags = options.defaultFlags || {};

    this.listeners = new Set();
    this.timer = null;
    this.eventSource = null;
    this.connectionMode = 'offline';
    this.lastEvaluatedAt = null;
    this.lastError = null;

    if (options.enableStream !== false && typeof EventSource !== 'undefined') {
      this.connectStream();
    } else {
      this.startPolling();
    }
  }

  public setContext(newContext: EvaluationContext): Promise<Record<string, boolean>> {
    this.context = {
      ...this.context,
      ...newContext,
      attributes: {
        ...(this.context.attributes || {}),
        ...(newContext.attributes || {}),
      },
    };
    return this.evaluate();
  }

  public setEnvironment(envSlug: string): Promise<Record<string, boolean>> {
    if (this.environment !== envSlug) {
      this.environment = envSlug;
      if (this.eventSource) {
        this.connectStream();
      }
      return this.evaluate();
    }
    return Promise.resolve(this.flags);
  }

  public connectStream(): void {
    this.closeStream();

    const streamUrl = `${this.streamEndpoint}?apiKey=${encodeURIComponent(this.apiKey)}&env=${encodeURIComponent(this.environment)}`;
    try {
      this.eventSource = new EventSource(streamUrl);

      this.eventSource.addEventListener('init', (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data);
          this.connectionMode = 'stream';
          this.handleIncomingFlags(data.flags || {}, data.evaluatedAt);
        } catch (err) {
          console.error('[AuraSDK] Failed to parse init event:', err);
        }
      });

      this.eventSource.addEventListener('flags_updated', (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data);
          this.connectionMode = 'stream';
          this.handleIncomingFlags(data.flags || {}, data.evaluatedAt);
        } catch (err) {
          console.error('[AuraSDK] Failed to parse flags_updated event:', err);
        }
      });

      this.eventSource.onerror = () => {
        // EventSource auto-retries; avoid stacking a second polling loop on every error.
        // Only fall back to polling after the stream is fully closed / unavailable.
        if (this.eventSource && this.eventSource.readyState === EventSource.CLOSED) {
          this.closeStream();
          if (this.connectionMode !== 'polling') {
            this.startPolling(this.pollIntervalMs);
          }
          return;
        }
        if (this.connectionMode === 'stream' && !this.timer) {
          // Soft degradation while EventSource reconnects in the background.
          this.connectionMode = 'polling';
          this.startPolling(this.pollIntervalMs);
        }
      };

      this.eventSource.onopen = () => {
        this.connectionMode = 'stream';
        this.stopPolling();
      };
    } catch {
      this.startPolling();
    }
  }

  public closeStream(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  private handleIncomingFlags(newFlags: Record<string, boolean>, evaluatedAt?: string): void {
    this.lastEvaluatedAt = evaluatedAt || new Date().toISOString();
    this.lastError = null;

    const changed: Record<string, FlagStateChange> = {};
    let hasChanges = false;

    for (const [key, val] of Object.entries(newFlags)) {
      if (this.flags[key] !== val) {
        changed[key] = { from: Boolean(this.flags[key]), to: Boolean(val) };
        hasChanges = true;
      }
    }

    this.flags = { ...this.flags, ...newFlags };
    this.notify(changed, !hasChanges);
  }

  public onUpdate(callback: AuraListener): () => void {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }

  private notify(changedFlags: Record<string, FlagStateChange>, isInitial = false): void {
    this.listeners.forEach((fn) => {
      try {
        fn({
          flags: { ...this.flags },
          changedFlags,
          environment: this.environment,
          context: { ...this.context },
          connectionMode: this.connectionMode,
          evaluatedAt: this.lastEvaluatedAt,
          isInitial,
        });
      } catch (err) {
        console.error('[AuraSDK] Error in listener callback:', err);
      }
    });
  }

  public isEnabled(key: string, defaultValue = false): boolean {
    return this.flags[key] !== undefined ? Boolean(this.flags[key]) : defaultValue;
  }

  public async evaluate(): Promise<Record<string, boolean>> {
    const hasContext = Boolean(
      this.context &&
        (this.context.userId ||
          this.context.email ||
          (this.context.attributes && Object.keys(this.context.attributes).length > 0))
    );

    const url = `${this.endpoint}?apiKey=${encodeURIComponent(this.apiKey)}&env=${encodeURIComponent(this.environment)}`;

    try {
      const response = await fetch(url, {
        method: hasContext ? 'POST' : 'GET',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: hasContext ? JSON.stringify(this.context) : undefined,
      });

      if (!response.ok) {
        throw new Error(`Aura API returned status ${response.status}`);
      }

      const data = (await response.json()) as { flags?: Record<string, boolean>; evaluatedAt?: string };
      this.handleIncomingFlags(data.flags || {}, data.evaluatedAt);
      return this.flags;
    } catch (err) {
      this.lastError = err instanceof Error ? err : new Error(String(err));
      return this.flags;
    }
  }

  public startPolling(intervalMs?: number): void {
    if (intervalMs) this.pollIntervalMs = intervalMs;
    this.stopPolling();
    this.connectionMode = 'polling';
    void this.evaluate();
    this.timer = setInterval(() => {
      void this.evaluate();
    }, this.pollIntervalMs);
  }

  public stopPolling(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}
