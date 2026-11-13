import React from 'react';
import { AuraClient, type AuraOptions, type EvaluationContext } from '@aura-flags/client';
export interface AuraProviderProps {
    client?: AuraClient;
    options?: AuraOptions;
    children: React.ReactNode;
}
/**
 * AuraProvider wraps your React component tree, establishing real-time SSE flag synchronization
 */
export declare const AuraProvider: React.FC<AuraProviderProps>;
/**
 * Hook to access the raw AuraClient instance
 */
export declare function useAuraClient(): AuraClient;
/**
 * Hook to evaluate a feature flag in real time with automatic re-renders on remote changes
 */
export declare function useFeatureFlag(flagKey: string, defaultValue?: boolean): boolean;
/**
 * Hook to update evaluation context dynamically (e.g. on login/logout)
 */
export declare function useAuraContext(): {
    setContext: (ctx: EvaluationContext) => Promise<Record<string, boolean>>;
    setEnvironment: (env: string) => Promise<Record<string, boolean>>;
    connectionMode: 'stream' | 'polling' | 'offline';
    flags: Record<string, boolean>;
};
export interface FeatureGateProps {
    flag: string;
    fallback?: React.ReactNode;
    children: React.ReactNode;
}
/**
 * Declarative component for conditional rendering based on flag state
 */
export declare const FeatureGate: React.FC<FeatureGateProps>;
//# sourceMappingURL=index.d.ts.map