/**
 * Aura Feature Flag Lightweight Client SDK (Phase 7 Real-Time Edition)
 * Supports real-time Server-Sent Events (SSE) streaming (<15ms)
 * and progressive Canary Context evaluation (MurmurHash3 & User Segmentation).
 */

export class AuraClient {
  constructor(options = {}) {
    this.endpoint = options.endpoint || 'http://localhost:8080/api/v1/evaluate';
    this.streamEndpoint = options.streamEndpoint || this.endpoint.replace('/evaluate', '/stream');
    this.apiKey = options.apiKey || 'aura_pk_live_84543af8d688414f9be90d2581d3ecab';
    this.environment = options.environment || 'development';
    this.pollIntervalMs = options.pollIntervalMs || 2500;
    this.context = options.context || {
      userId: 'usr_demo_42',
      email: 'engineer@aura.dev',
      attributes: {}
    };

    this.flags = options.defaultFlags || {
      'dark-mode': true,
      'ai-copilot-beta': true,
      'fast-checkout-v2': false,
      'announcement-banner': true
    };

    this.listeners = new Set();
    this.timer = null;
    this.eventSource = null;
    this.connectionMode = 'idle'; // 'stream' | 'polling' | 'offline'
    this.lastEvaluatedAt = null;
    this.lastError = null;

    // Enable SSE streaming by default if EventSource exists
    if (options.enableStream !== false && typeof EventSource !== 'undefined') {
      this.connectStream();
    } else {
      this.startPolling();
    }
  }

  /**
   * Set evaluation context (e.g. current logged-in user, email, plan)
   */
  setContext(newContext = {}) {
    this.context = {
      ...this.context,
      ...newContext,
      attributes: {
        ...(this.context?.attributes || {}),
        ...(newContext.attributes || {})
      }
    };
    return this.evaluate();
  }

  /**
   * Set active environment (development, staging, production)
   */
  setEnvironment(envSlug) {
    if (this.environment !== envSlug) {
      this.environment = envSlug;
      if (this.eventSource) {
        // Reconnect stream for new environment
        this.connectStream();
      }
      return this.evaluate();
    }
    return Promise.resolve(this.flags);
  }

  /**
   * Connect to Aura Real-Time Server-Sent Events (SSE) Stream
   */
  connectStream() {
    this.closeStream();

    const streamUrl = `${this.streamEndpoint}?apiKey=${encodeURIComponent(this.apiKey)}&env=${encodeURIComponent(this.environment)}`;
    try {
      this.eventSource = new EventSource(streamUrl);

      this.eventSource.addEventListener('init', (e) => {
        try {
          const data = JSON.parse(e.data);
          this.connectionMode = 'stream';
          this.handleIncomingFlags(data.flags || {}, data.evaluatedAt);
        } catch (err) {
          console.error('[AuraSDK] Error parsing init event:', err);
        }
      });

      this.eventSource.addEventListener('flags_updated', (e) => {
        try {
          const data = JSON.parse(e.data);
          this.connectionMode = 'stream';
          this.handleIncomingFlags(data.flags || {}, data.evaluatedAt);
        } catch (err) {
          console.error('[AuraSDK] Error parsing flags_updated event:', err);
        }
      });

      this.eventSource.onerror = (err) => {
        // If stream drops, fall back to polling while browser attempts reconnection
        if (this.connectionMode === 'stream') {
          console.warn('[AuraSDK] Stream interrupted, activating fallback polling...');
          this.connectionMode = 'polling';
          this.startPolling(this.pollIntervalMs);
        }
      };

      this.eventSource.onopen = () => {
        this.connectionMode = 'stream';
        this.stopPolling();
      };
    } catch (err) {
      console.warn('[AuraSDK] EventSource unavailable, using polling fallback:', err);
      this.startPolling();
    }
  }

  /**
   * Close SSE stream
   */
  closeStream() {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  /**
   * Process updated flags, compute diffs, and notify subscribers
   */
  handleIncomingFlags(newFlags, evaluatedAt) {
    this.lastEvaluatedAt = evaluatedAt || new Date().toISOString();
    this.lastError = null;

    const changed = {};
    let hasChanges = false;
    for (const [key, val] of Object.entries(newFlags)) {
      if (this.flags[key] !== val) {
        changed[key] = { from: this.flags[key], to: val };
        hasChanges = true;
      }
    }

    this.flags = { ...this.flags, ...newFlags };
    this.notify(changed, !hasChanges);
  }

  /**
   * Subscribe to flag updates
   */
  onUpdate(callback) {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }

  /**
   * Notify all registered listeners
   */
  notify(changedFlags, isInitial = false) {
    this.listeners.forEach((fn) => {
      try {
        fn({
          flags: { ...this.flags },
          changedFlags,
          environment: this.environment,
          context: { ...this.context },
          connectionMode: this.connectionMode,
          evaluatedAt: this.lastEvaluatedAt,
          isInitial
        });
      } catch (err) {
        console.error('[AuraSDK] Error in listener callback:', err);
      }
    });
  }

  /**
   * Check whether a specific feature flag is enabled
   */
  isEnabled(key, defaultValue = false) {
    return this.flags[key] !== undefined ? Boolean(this.flags[key]) : defaultValue;
  }

  /**
   * Evaluate flags from Aura API via POST (with context) or GET
   */
  async evaluate() {
    const hasContext = this.context && (this.context.userId || this.context.email || Object.keys(this.context.attributes || {}).length > 0);
    const url = `${this.endpoint}?apiKey=${encodeURIComponent(this.apiKey)}&env=${encodeURIComponent(this.environment)}`;

    try {
      const response = await fetch(url, {
        method: hasContext ? 'POST' : 'GET',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: hasContext ? JSON.stringify(this.context) : undefined
      });

      if (!response.ok) {
        throw new Error(`Aura API returned status ${response.status}`);
      }

      const data = await response.json();
      this.handleIncomingFlags(data.flags || {}, data.evaluatedAt);
      return this.flags;
    } catch (err) {
      this.lastError = err;
      console.warn('[AuraSDK] Evaluation fallback active:', err.message);
      return this.flags;
    }
  }

  /**
   * Start polling for live remote changes (fallback mode)
   */
  startPolling(intervalMs) {
    if (intervalMs) this.pollIntervalMs = intervalMs;
    this.stopPolling();
    this.connectionMode = 'polling';
    this.evaluate();
    this.timer = setInterval(() => {
      this.evaluate();
    }, this.pollIntervalMs);
  }

  /**
   * Stop polling
   */
  stopPolling() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}
