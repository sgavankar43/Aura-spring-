import { jsx as _jsx, Fragment as _Fragment } from "react/jsx-runtime";
import React, { createContext, useContext, useEffect, useState, useMemo } from 'react';
import { AuraClient } from '@aura-flags/client';
const AuraContext = createContext({
    client: null,
    flags: {},
    connectionMode: 'offline',
    evaluatedAt: null,
});
/**
 * AuraProvider wraps your React component tree, establishing real-time SSE flag synchronization
 */
export const AuraProvider = ({ client, options, children }) => {
    const auraClient = useMemo(() => {
        if (client)
            return client;
        if (options)
            return new AuraClient(options);
        throw new Error('[AuraReact] Either client or options must be passed to AuraProvider');
    }, [client, options]);
    const [flags, setFlags] = useState(auraClient.flags);
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
    const value = useMemo(() => ({
        client: auraClient,
        flags,
        connectionMode,
        evaluatedAt,
    }), [auraClient, flags, connectionMode, evaluatedAt]);
    return _jsx(AuraContext.Provider, { value: value, children: children });
};
/**
 * Hook to access the raw AuraClient instance
 */
export function useAuraClient() {
    const ctx = useContext(AuraContext);
    if (!ctx.client) {
        throw new Error('[AuraReact] useAuraClient must be used within an <AuraProvider>');
    }
    return ctx.client;
}
/**
 * Hook to evaluate a feature flag in real time with automatic re-renders on remote changes
 */
export function useFeatureFlag(flagKey, defaultValue = false) {
    const { flags, client } = useContext(AuraContext);
    if (flags[flagKey] !== undefined) {
        return Boolean(flags[flagKey]);
    }
    return client ? client.isEnabled(flagKey, defaultValue) : defaultValue;
}
/**
 * Hook to update evaluation context dynamically (e.g. on login/logout)
 */
export function useAuraContext() {
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
/**
 * Declarative component for conditional rendering based on flag state
 */
export const FeatureGate = ({ flag, fallback = null, children }) => {
    const isEnabled = useFeatureFlag(flag);
    return _jsx(_Fragment, { children: isEnabled ? children : fallback });
};
