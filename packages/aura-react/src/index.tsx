import React, { createContext, useContext, useEffect, useState, useMemo } from 'react';
import { AuraClient, type AuraOptions, type EvaluationContext } from '@aura-flags/client';

interface AuraContextValue {
  client: AuraClient | null;
  flags: Record<string, boolean>;
  connectionMode: 'stream' | 'polling' | 'offline';
  evaluatedAt: string | null;
}

const AuraContext = createContext<AuraContextValue>({
  client: null,
  flags: {},
  connectionMode: 'offline',
  evaluatedAt: null,
});

export interface AuraProviderProps {
  client?: AuraClient;
  options?: AuraOptions;
  children: React.ReactNode;
}

/**
 * AuraProvider wraps your React component tree, establishing real-time SSE flag synchronization
 */
export const AuraProvider: React.FC<AuraProviderProps> = ({ client, options, children }) => {
  const auraClient = useMemo(() => {
    if (client) return client;
    if (options) return new AuraClient(options);
    throw new Error('[AuraReact] Either client or options must be passed to AuraProvider');
  }, [client, options]);

  const [flags, setFlags] = useState<Record<string, boolean>>(auraClient.flags);
  const [connectionMode, setConnectionMode] = useState(auraClient.connectionMode);
  const [evaluatedAt, setEvaluatedAt] = useState(auraClient.lastEvaluatedAt);

  useEffect(() => {
    const unsubscribe = auraClient.onUpdate((state) => {
      setFlags({ ...state.flags });
      setConnectionMode(state.connectionMode);
      setEvaluatedAt(state.evaluatedAt);
    });

    return () => {
      unsubscribe();
    };
  }, [auraClient]);

  const value = useMemo(
    () => ({
      client: auraClient,
      flags,
      connectionMode,
      evaluatedAt,
    }),
    [auraClient, flags, connectionMode, evaluatedAt]
  );

  return <AuraContext.Provider value={value}>{children}</AuraContext.Provider>;
};

/**
 * Hook to access the raw AuraClient instance
 */
export function useAuraClient(): AuraClient {
  const ctx = useContext(AuraContext);
  if (!ctx.client) {
    throw new Error('[AuraReact] useAuraClient must be used within an <AuraProvider>');
  }
  return ctx.client;
}

/**
 * Hook to evaluate a feature flag in real time with automatic re-renders on remote changes
 */
export function useFeatureFlag(flagKey: string, defaultValue = false): boolean {
  const { flags, client } = useContext(AuraContext);
  if (flags[flagKey] !== undefined) {
    return Boolean(flags[flagKey]);
  }
  return client ? client.isEnabled(flagKey, defaultValue) : defaultValue;
}

/**
 * Hook to update evaluation context dynamically (e.g. on login/logout)
 */
export function useAuraContext(): {
  setContext: (ctx: EvaluationContext) => Promise<Record<string, boolean>>;
  setEnvironment: (env: string) => Promise<Record<string, boolean>>;
  connectionMode: 'stream' | 'polling' | 'offline';
  flags: Record<string, boolean>;
} {
  const { client, flags, connectionMode } = useContext(AuraContext);
  if (!client) {
    throw new Error('[AuraReact] useAuraContext must be used within an <AuraProvider>');
  }

  return {
    setContext: (ctx) => client.setContext(ctx),
    setEnvironment: (env) => client.setEnvironment(env),
    connectionMode,
    flags,
  };
}

export interface FeatureGateProps {
  flag: string;
  fallback?: React.ReactNode;
  children: React.ReactNode;
}

/**
 * Declarative component for conditional rendering based on flag state
 */
export const FeatureGate: React.FC<FeatureGateProps> = ({ flag, fallback = null, children }) => {
  const isEnabled = useFeatureFlag(flag);
  return <>{isEnabled ? children : fallback}</>;
};
