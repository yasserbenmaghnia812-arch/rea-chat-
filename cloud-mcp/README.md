# REA Cloud MCP

Public gateway for the official rea-agents MCP server. **No authentication is enabled. Anyone who can reach the public URL may send MCP requests.**

- GET /health: health status (no authentication).
- /mcp: Streamable HTTP (no authentication).
- REA listens only on 127.0.0.1:8766; Render provides public HTTPS.
- Deploy from the repository root using render.yaml. autoDeploy is disabled.
- Render Free may sleep when idle; local files are ephemeral.
