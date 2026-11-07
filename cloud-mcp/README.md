# REA Cloud MCP
Authenticated gateway for the official rea-agents MCP server.

- GET /health: health status.
- /mcp: Streamable HTTP; requires Authorization: Bearer <token>.
- REA listens only on 127.0.0.1:8766; Render provides public HTTPS.
- Set MCP_BEARER_TOKEN in Render to a random secret of at least 32 characters.
- Deploy from the repository root using render.yaml. autoDeploy is disabled.
- REA's HTTP transport itself has no TLS/authentication; this gateway protects it.
- REA currently serves one active MCP session at a time.
- Render Free may sleep when idle; local files are ephemeral.
