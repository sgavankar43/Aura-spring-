/**
 * Official TypeScript Client SDK for Aura Distributed Feature Flag Platform
 */
export class AuraClient {
    endpoint;
    streamEndpoint;
    apiKey;
    environment;
    pollIntervalMs;
    context;
    flags;
    listeners;
    timer;
    eventSource;
    connectionMode;
    lastEvaluatedAt;
    lastError;
    constructor(options) {
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
        }
        else {
            this.startPolling();
        }
    }
    setContext(newContext) {
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
    setEnvironment(envSlug) {
        if (this.environment !== envSlug) {
            this.environment = envSlug;
            if (this.eventSource) {
                this.connectStream();
            }
            return this.evaluate();
        }
        return Promise.resolve(this.flags);
    }
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
                }
                catch (err) {
                    console.error('[AuraSDK] Failed to parse init event:', err);
                }
            });
            this.eventSource.addEventListener('flags_updated', (e) => {
                try {
                    const data = JSON.parse(e.data);
                    this.connectionMode = 'stream';
                    this.handleIncomingFlags(data.flags || {}, data.evaluatedAt);
                }
                catch (err) {
                    console.error('[AuraSDK] Failed to parse flags_updated event:', err);
                }
            });
            this.eventSource.onerror = () => {
                if (this.connectionMode === 'stream') {
                    this.connectionMode = 'polling';
                    this.startPolling(this.pollIntervalMs);
                }
            };
            this.eventSource.onopen = () => {
                this.connectionMode = 'stream';
                this.stopPolling();
            };
        }
        catch {
            this.startPolling();
        }
    }
    closeStream() {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }
    }
    handleIncomingFlags(newFlags, evaluatedAt) {
        this.lastEvaluatedAt = evaluatedAt || new Date().toISOString();
        this.lastError = null;
        const changed = {};
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
    onUpdate(callback) {
        this.listeners.add(callback);
        return () => this.listeners.delete(callback);
    }
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
                    isInitial,
                });
            }
            catch (err) {
                console.error('[AuraSDK] Error in listener callback:', err);
            }
        });
    }
    isEnabled(key, defaultValue = false) {
        return this.flags[key] !== undefined ? Boolean(this.flags[key]) : defaultValue;
    }
    async evaluate() {
        const hasContext = Boolean(this.context &&
            (this.context.userId ||
                this.context.email ||
                (this.context.attributes && Object.keys(this.context.attributes).length > 0)));
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
            const data = (await response.json());
            this.handleIncomingFlags(data.flags || {}, data.evaluatedAt);
            return this.flags;
        }
        catch (err) {
            this.lastError = err instanceof Error ? err : new Error(String(err));
            return this.flags;
        }
    }
    startPolling(intervalMs) {
        if (intervalMs)
            this.pollIntervalMs = intervalMs;
        this.stopPolling();
        this.connectionMode = 'polling';
        void this.evaluate();
        this.timer = setInterval(() => {
            void this.evaluate();
        }, this.pollIntervalMs);
    }
    stopPolling() {
        if (this.timer) {
            clearInterval(this.timer);
            this.timer = null;
        }
    }
}
