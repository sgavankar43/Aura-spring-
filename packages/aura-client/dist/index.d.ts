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
export declare class AuraClient {
    private endpoint;
    private streamEndpoint;
    private apiKey;
    environment: string;
    private pollIntervalMs;
    context: EvaluationContext;
    flags: Record<string, boolean>;
    private listeners;
    private timer;
    private eventSource;
    connectionMode: 'stream' | 'polling' | 'offline';
    lastEvaluatedAt: string | null;
    lastError: Error | null;
    constructor(options: AuraOptions);
    setContext(newContext: EvaluationContext): Promise<Record<string, boolean>>;
    setEnvironment(envSlug: string): Promise<Record<string, boolean>>;
    connectStream(): void;
    closeStream(): void;
    private handleIncomingFlags;
    onUpdate(callback: AuraListener): () => void;
    private notify;
    isEnabled(key: string, defaultValue?: boolean): boolean;
    evaluate(): Promise<Record<string, boolean>>;
    startPolling(intervalMs?: number): void;
    stopPolling(): void;
}
//# sourceMappingURL=index.d.ts.map