# sampleMCP

A minimal remote [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server
written in Java, built with Spring Boot and the official
[MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk). It's designed to be
deployed to [Render](https://render.com) and added to Claude Desktop as a **custom
connector**.

It exposes three toy tools so you can confirm everything is wired up end to end,
plus two optional tools that call into a Salesforce org via Apex REST:

| Tool                     | What it does                                                          |
|--------------------------|------------------------------------------------------------------------|
| `echo`                   | Echoes back the text you send it.                                     |
| `get_current_time`       | Returns the current time, optionally in a given time zone.            |
| `add_numbers`            | Adds two numbers together.                                            |
| `salesforce_get_account` | *(optional, see below)* Looks up a Salesforce Account by name.        |
| `salesforce_create_case` | *(optional, see below)* Creates a Salesforce Case.                    |

## How it's built

- **Transport**: Streamable HTTP (the current recommended MCP transport for remote
  servers), served at `POST/GET/DELETE /mcp` via `HttpServletStreamableServerTransportProvider`
  from `io.modelcontextprotocol.sdk:mcp`, registered as a plain servlet in Spring Boot's
  embedded Tomcat.
- **Framework**: Spring Boot 4.1.1 (Java 17), used only to host the servlet over HTTP —
  there's no Spring AI / Spring MVC magic involved, tools are registered directly against
  the MCP SDK in `McpServerConfig`.
- Core wiring lives in two classes: `SampleMcpApplication` (entry point) and
  `McpServerConfig` (transport + tool registration).
- The optional Salesforce integration lives in `org.shoaeb.mcp.salesforce`
  (`SalesforceAuthService`, `SalesforceClient`, `SalesforceMcpTools`) — see
  "Connect to Salesforce" below.

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
   `https://sample-mcp-server-cckz.onrender.com`. Verify it:
   ```bash
   curl https://sample-mcp-server-cckz.onrender.com/
   ```

   > Note: on Render's free plan the service spins down after inactivity and takes a
   > few seconds to wake back up on the next request — the first tool call from Claude
   > after idle time may be slow.

Your MCP endpoint is now: `https://sample-mcp-server-cckz.onrender.com/mcp`

## Add it to Claude Desktop as a custom connector

1. Open Claude Desktop → **Settings** (`Ctrl+,`) → **Connectors**.
2. Click **Add** → **Add custom connector**.
3. Enter the MCP endpoint URL from above, e.g.
   `https://sample-mcp-server-cckz.onrender.com/mcp`, and click **Add**.
   (This sample server has no authentication, so there's no OAuth client
   ID/secret to fill in under Advanced settings.)
4. Once connected, start a chat, click **Add files, connectors, and more** (`+`) in the
   message box, and enable the connector's tools. Claude can now call `echo`,
   `get_current_time`, and `add_numbers` on your Render-hosted server.

## Connect to Salesforce: invoke Apex REST APIs

`salesforce_get_account` and `salesforce_create_case` call a small Apex REST class
deployed in your org (`salesforce/force-app/main/default/classes/McpDemoService.cls`)
over HTTPS, authenticating with the OAuth 2.0 **JWT Bearer flow** — no interactive
login, no stored password, no client secret. They only appear in `tools/list` once
Salesforce credentials are configured; without them the server starts up fine and just
skips registering the two tools (see the startup log line from `McpServerConfig`).

### 1. Generate a certificate (already done for you)

The JWT Bearer flow needs an RSA key pair: Salesforce gets the public certificate,
your server keeps the private key and signs with it. This has already been generated
and is sitting in `salesforce/certs/` (gitignored — never commit it):

- `salesforce/certs/server.crt` — public certificate, **upload this to Salesforce**.
- `salesforce/certs/server_pkcs8.key` — private key, **this goes into Render as a
  secret env var**. Treat it like a password: don't paste it in chat, Slack, tickets, etc.

To regenerate (e.g. to rotate it later):
```bash
cd salesforce/certs
openssl req -x509 -sha256 -nodes -newkey rsa:2048 -keyout server.key -out server.crt \
  -days 3650 -subj "/CN=sample-mcp-server/O=sampleMCP"
openssl pkcs8 -topk8 -nocrypt -in server.key -out server_pkcs8.key
rm server.key
```

### 2. Create a Connected App in Salesforce

In your org: **Setup → App Manager → New Connected App**.

1. Fill in **Connected App Name** (e.g. `Sample MCP Server`) and **Contact Email**.
2. Check **Enable OAuth Settings**.
3. **Callback URL**: this flow never redirects a browser here, but Salesforce requires
   a value — use `https://login.salesforce.com/services/oauth2/callback`.
4. **Selected OAuth Scopes**: add *"Manage user data via APIs (api)"*.
5. Check **Use digital signatures**, then **Upload** `salesforce/certs/server.crt`.
6. Save. (Salesforce says it can take ~10 minutes to activate — it's usually much faster.)
7. On the Connected App's **Manage** page → **Edit Policies**:
   - Set **Permitted Users** to **Admin approved users are pre-authorized** (so no one
     has to click through an OAuth consent screen — there's no browser involved anyway).
8. Still on **Manage**, under **Profiles** or **Permission Sets**, add the profile/
   permission set of the Salesforce user you want the server to act as (a dedicated
   integration user is best practice, but your own user works for a quick test).
9. Copy the **Consumer Key** from the app's page — that's `SF_CLIENT_ID` below.

### 3. Deploy the Apex class

The class lives in a small Salesforce DX project under `salesforce/`. With the
[Salesforce CLI](https://developer.salesforce.com/tools/salesforcecli) installed:

```bash
cd salesforce
sf org login web -d          # authenticates and sets this org as default
sf project deploy start
```

No CLI? Paste the contents of `McpDemoService.cls` manually via **Setup → Apex Classes
→ New**, or use the Developer Console's Apex class editor.

### 4. Configure the server

Set these on Render (**Dashboard → your service → Environment**) — and locally in your
shell for testing:

| Env var             | Value                                                                 |
|----------------------|------------------------------------------------------------------------|
| `SF_CLIENT_ID`       | The Connected App's Consumer Key.                                     |
| `SF_USERNAME`        | Exact Salesforce username of the user the app is pre-authorized for.  |
| `SF_PRIVATE_KEY_PEM` | Full contents of `salesforce/certs/server_pkcs8.key`, including the `-----BEGIN/END PRIVATE KEY-----` lines. |
| `SF_LOGIN_URL`       | *(optional)* Defaults to `https://login.salesforce.com`. Use `https://test.salesforce.com` for a sandbox. |

Save, let Render redeploy, then confirm both tools showed up:

```bash
curl -s -X POST https://sample-mcp-server-cckz.onrender.com/mcp \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' -D -
# grab the Mcp-Session-Id header from the response, then:
curl -s -X POST https://sample-mcp-server-cckz.onrender.com/mcp \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: <paste it>" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

Then just ask Claude, e.g. *"Look up the Salesforce Account named Acme"* or *"Create a
Salesforce case titled 'Test from Claude'"*.

### How the auth works, briefly

`SalesforceAuthService` builds a JWT by hand (header/payload/signature, base64url — no
JWT library needed) with `iss=SF_CLIENT_ID`, `sub=SF_USERNAME`, `aud=SF_LOGIN_URL`, signs
it with the private key via `Signature.getInstance("SHA256withRSA")`, and POSTs it to
`{SF_LOGIN_URL}/services/oauth2/token`. Salesforce validates the signature against the
certificate you uploaded and returns an access token + your org's `instance_url`.
`SalesforceClient` then calls `{instance_url}/services/apexrest/mcp-demo/...` with that
token as a Bearer header, and re-authenticates once automatically if it ever gets a 401.

## Adding your own tools

Add a new `Tool.builder(name, inputSchema).description(...).build()` plus a handler
method, then register it with `.toolCall(tool, this::handler)` in
`McpServerConfig#mcpSyncServer`. Follow the existing `echo`/`add_numbers` tools as a
template — see the [MCP Java SDK docs](https://java.sdk.modelcontextprotocol.io) for the
full API surface (resources, prompts, async servers, etc).
