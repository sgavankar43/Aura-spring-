package com.aura.controller;

import com.aura.service.McpServerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpServerService mcpServerService;

    public McpController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    /**
     * MCP Tools Manifest - returns schemas for Cursor, Antigravity, and Claude Desktop
     */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        return ResponseEntity.ok(Map.of(
                "tools", mcpServerService.getToolsManifest()
        ));
    }

    /**
     * MCP JSON-RPC tool invocation endpoint
     */
    @PostMapping("/rpc")
    public ResponseEntity<Map<String, Object>> executeRpc(@RequestBody Map<String, Object> body) {
        String method = (String) body.get("method");
        Object id = body.get("id");

        if ("tools/list".equals(method)) {
            return ResponseEntity.ok(Map.of(
                    "jsonrpc", "2.0",
                    "id", id != null ? id : 1,
                    "result", Map.of("tools", mcpServerService.getToolsManifest())
            ));
        }

        if ("tools/call".equals(method)) {
            Map<String, Object> params = (Map<String, Object>) body.get("params");
            String toolName = (String) params.get("name");
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

            Map<String, Object> toolResult = mcpServerService.executeTool(toolName, arguments);
            return ResponseEntity.ok(Map.of(
                    "jsonrpc", "2.0",
                    "id", id != null ? id : 1,
                    "result", Map.of(
                            "content", List.of(Map.of(
                                    "type", "text",
                                    "text", toolResult.toString()
                            )),
                            "data", toolResult
                    )
            ));
        }

        return ResponseEntity.ok(Map.of(
                "jsonrpc", "2.0",
                "id", id != null ? id : 1,
                "error", Map.of("code", -32601, "message", "Method not found: " + method)
        ));
    }

    /**
     * Simple direct tool invocation endpoint: POST /api/mcp/tools/{toolName}
     */
    @PostMapping("/tools/{toolName}")
    public ResponseEntity<Map<String, Object>> callToolDirect(
            @PathVariable String toolName,
            @RequestBody Map<String, Object> args) {
        return ResponseEntity.ok(mcpServerService.executeTool(toolName, args));
    }
}
