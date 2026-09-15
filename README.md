# sampleMCP

A minimal remote [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server
written in Java, built with Spring Boot and the official
[MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk). It's designed to be
deployed to [Render](https://render.com) and added to Claude Desktop as a **custom
connector**.

It exposes three toy tools so you can confirm everything is wired up end to end:

| Tool               | What it does                                              |
|---------------------|------------------------------------------------------------|
| `echo`              | Echoes back the text you send it.                          |
| `get_current_time`  | Returns the current time, optionally in a given time zone.|
| `add_numbers`       | Adds two numbers together.                                  |

## How it's built

- **Transport**: Streamable HTTP (the current recommended MCP transport for remote
  servers), served at `POST/GET/DELETE /mcp` via `HttpServletStreamableServerTransportProvider`
  from `io.modelcontextprotocol.sdk:mcp`, registered as a plain servlet in Spring Boot's
  embedded Tomcat.
- **Framework**: Spring Boot 4.1.1 (Java 17), used only to host the servlet over HTTP —
  there's no Spring AI / Spring MVC magic involved, tools are registered directly against
  the MCP SDK in `McpServerConfig`.
- All the wiring lives in two classes: `SampleMcpApplication` (entry point) and
  `McpServerConfig` (transport + tool registration).

## Run it locally

```bash
mvn spring-boot:run
```

The server listens on `:8080` by default (override with `PORT`). Sanity check:

```bash
curl http://localhost:8080/
# sample-mcp-server is running. Point an MCP client at /mcp
```

To talk MCP directly (useful for debugging), POST a JSON-RPC `initialize` request to
`/mcp` — see the MCP spec for the full handshake, or just point an MCP Inspector /
Claude Desktop connector at `http://localhost:8080/mcp`.

## Deploy to Render

Render doesn't have a native Java runtime, so this repo ships a multi-stage
`Dockerfile` (builds with Maven, runs on a slim JRE) and a `render.yaml` blueprint.

1. **Push this repo to GitHub** (Render deploys from a connected Git repo):
   ```bash
   git init
   git add -A
   git commit -m "Initial commit: sample MCP server"
   git branch -M main
   git remote add origin https://github.com/<your-username>/sampleMCP.git
   git push -u origin main
   ```
2. **Create the Render service**:
   - Easiest: go to <https://dashboard.render.com/blueprints>, click **New Blueprint
     Instance**, pick this repo — Render will read `render.yaml` and configure
     everything (Docker runtime, health check on `/`) automatically.
   - Or manually: **New > Web Service** → connect the repo → Render should
     auto-detect the `Dockerfile`; if asked, set **Environment/Runtime** to
     **Docker**. No extra environment variables are required — Render provides
     `PORT` automatically and `application.properties` already reads it.
3. Wait for the build to finish. Render gives you a public URL like
   `https://sample-mcp-server.onrender.com`. Verify it:
   ```bash
   curl https://sample-mcp-server.onrender.com/
   ```

   > Note: on Render's free plan the service spins down after inactivity and takes a
   > few seconds to wake back up on the next request — the first tool call from Claude
   > after idle time may be slow.

Your MCP endpoint is now: `https://sample-mcp-server.onrender.com/mcp`

## Add it to Claude Desktop as a custom connector

1. Open Claude Desktop → **Settings** (`Ctrl+,`) → **Connectors**.
2. Click **Add** → **Add custom connector**.
3. Enter the MCP endpoint URL from above, e.g.
   `https://sample-mcp-server.onrender.com/mcp`, and click **Add**.
   (This sample server has no authentication, so there's no OAuth client
   ID/secret to fill in under Advanced settings.)
4. Once connected, start a chat, click **Add files, connectors, and more** (`+`) in the
   message box, and enable the connector's tools. Claude can now call `echo`,
   `get_current_time`, and `add_numbers` on your Render-hosted server.

## Adding your own tools

Add a new `Tool.builder(name, inputSchema).description(...).build()` plus a handler
method, then register it with `.toolCall(tool, this::handler)` in
`McpServerConfig#mcpSyncServer`. Follow the existing `echo`/`add_numbers` tools as a
template — see the [MCP Java SDK docs](https://java.sdk.modelcontextprotocol.io) for the
full API surface (resources, prompts, async servers, etc).
