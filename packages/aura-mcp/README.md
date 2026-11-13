# @aura-flags/mcp

**Aura Feature Flag Model Context Protocol (MCP) Server**

Enables AI Coding Agents (such as **Cursor**, **Antigravity**, **VS Code Claude Dev**, and **Claude Desktop**) to view, evaluate, and toggle Aura feature flags directly from chat.

---

## 🚀 Quick Setup for Cursor / Antigravity

Add the following to your `claude_desktop_config.json` or `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "aura": {
      "command": "node",
      "args": ["/Users/sahilgavankar/Desktop/MEGA/packages/aura-mcp/dist/index.js"],
      "env": {
        "AURA_API_KEY": "your_aura_project_api_key",
        "AURA_API_URL": "http://localhost:8080"
      }
    }
  }
}
```

---

## 🛠️ Available MCP Tools

| Tool | Description |
| :--- | :--- |
| **`list_flags`** | Lists all feature flags in the project with states across environments and canary rollout percentages. |
| **`get_flag`** | Retrieves full details, targeting rules, and states for a specific flag. |
| **`toggle_flag`** | Toggles a flag ON or OFF in a target environment (`development`, `staging`, `production`). |
| **`update_rollout`** | Adjusts canary rollout percentage (0-100%) or rules for a flag. |
| **`evaluate_flags`** | Simulates deterministic flag evaluation for a user context (`userId`, `email`, `role`). |
| **`get_tech_debt`** | Analyzes technical debt and detects stale flags ready for retirement. |
| **`cleanup_flag`** | Generates an automated Git refactoring diff to retire a stale flag. |

---

## 💡 Example Prompt for Cursor / Antigravity

> *"List all active feature flags in my Aura project and check if `fast-checkout-v2` is enabled in production."*
