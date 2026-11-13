#!/usr/bin/env node
/**
 * Aura Model Context Protocol (MCP) Server
 * Enables AI Agents (Cursor, Antigravity, VS Code, Claude) to manage and evaluate feature flags.
 */
import * as readline from 'node:readline';
const AURA_API_URL = process.env.AURA_API_URL || 'http://localhost:8080';
const DEFAULT_API_KEY = process.env.AURA_API_KEY || '';
async function fetchAuraRpc(request) {
    const url = `${AURA_API_URL.replace(/\/$/, '')}/api/mcp/rpc`;
    const res = await fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'User-Agent': 'Aura-MCP-Client/1.0'
        },
        body: JSON.stringify(request)
    });
    if (!res.ok) {
        throw new Error(`Aura Backend HTTP ${res.status}: ${await res.text()}`);
    }
    return res.json();
}
const SERVER_CAPABILITIES = {
    protocolVersion: '2024-11-05',
    capabilities: {
        tools: {
            listChanged: false
        }
    },
    serverInfo: {
        name: 'aura-mcp-server',
        version: '1.0.0'
    }
};
async function handleRpcMessage(msgStr) {
    if (!msgStr.trim())
        return null;
    let req;
    try {
        req = JSON.parse(msgStr);
    }
    catch (err) {
        return {
            jsonrpc: '2.0',
            id: 0,
            error: { code: -32700, message: `Parse error: ${err.message}` }
        };
    }
    const id = req.id ?? 1;
    // 1. MCP initialize handshake
    if (req.method === 'initialize') {
        return {
            jsonrpc: '2.0',
            id,
            result: SERVER_CAPABILITIES
        };
    }
    // 2. notifications
    if (req.method === 'notifications/initialized') {
        return null;
    }
    // 3. tools/list
    if (req.method === 'tools/list') {
        try {
            const backendResp = await fetchAuraRpc(req);
            return backendResp;
        }
        catch (err) {
            process.stderr.write(`[Aura MCP] Error fetching tools/list: ${err.message}\n`);
            return {
                jsonrpc: '2.0',
                id,
                error: { code: -32603, message: `Failed to connect to Aura Backend: ${err.message}` }
            };
        }
    }
    // 4. tools/call
    if (req.method === 'tools/call') {
        try {
            // Inject default API key if not provided in tool arguments
            if (req.params?.arguments && !req.params.arguments.apiKey && DEFAULT_API_KEY) {
                req.params.arguments.apiKey = DEFAULT_API_KEY;
            }
            const backendResp = await fetchAuraRpc(req);
            return backendResp;
        }
        catch (err) {
            process.stderr.write(`[Aura MCP] Error calling tool: ${err.message}\n`);
            return {
                jsonrpc: '2.0',
                id,
                error: { code: -32603, message: `Tool execution failed: ${err.message}` }
            };
        }
    }
    // Unknown method
    return {
        jsonrpc: '2.0',
        id,
        error: { code: -32601, message: `Method not found: ${req.method}` }
    };
}
// Start stdio interface
const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
});
rl.on('line', async (line) => {
    try {
        const response = await handleRpcMessage(line);
        if (response) {
            process.stdout.write(JSON.stringify(response) + '\n');
        }
    }
    catch (err) {
        process.stderr.write(`[Aura MCP Unhandled Error] ${err.stack || err.message}\n`);
    }
});
process.stderr.write(`[Aura MCP Server] Initialized. Target: ${AURA_API_URL}\n`);
