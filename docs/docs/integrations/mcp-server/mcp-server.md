# MCP server

## Introduction

OpenAEV can be used from any [Model Context Protocol (MCP)](https://modelcontextprotocol.io/)-compatible client — Cursor, Claude Desktop, Claude Code, or custom AI agents — through the native MCP server embedded in [XTM One](https://docs.xtmone.io/).

When an OpenAEV instance is registered with XTM One, XTM One automatically exposes an MCP server for it. AI clients connect to XTM One and work with your adversarial exposure validation content: scenarios, simulations, payloads, injects, atomic tests, expectations, findings and vulnerabilities.

!!! note "Community feature"

    The OpenAEV MCP server is available in the Community Edition of XTM One. There is nothing to install on the OpenAEV side: the server activates as soon as the platform is registered with XTM One.

## Architecture

```
MCP client (Cursor, Claude Desktop, ...)
        |  Streamable HTTP (JSON-RPC 2.0)
        |  Authorization: Bearer <XTM One API key>
        v
XTM One   POST /mcp/openaev
        |  short-lived per-user token (verified via JWKS)
        v
OpenAEV   REST API (acting as the calling user)
```

- The MCP endpoint is served by XTM One at `https://<your-xtm-one>/mcp/openaev` using the MCP Streamable HTTP transport.
- Clients authenticate with a personal XTM One API key (`fcp-...`) or an OAuth 2.1 access token.
- For every tool call, XTM One signs a short-lived token for the calling user (matched by email in OpenAEV), so all operations run with that user's permissions and audit trail. No long-lived OpenAEV credential is ever stored in XTM One or exposed to the MCP client: the only secret the client holds is its XTM One API key, and OpenAEV verifies the short-lived tokens through the XTM One JWKS (JSON Web Key Set).

## Prerequisites

1. A running XTM One instance.
2. The OpenAEV platform registered with XTM One (the same registration that powers the embedded AI experience).
3. A user account existing on both platforms with the same email address.
4. A personal API key created in XTM One (**My Profile > API Keys**).

## Capabilities

The MCP server exposes the business and knowledge tool surface of OpenAEV:

| Category | Examples |
| -------- | -------- |
| Search & read | full-text search, scenarios, simulations (exercises), payloads, injector contracts, findings, CVEs, assets, asset groups, teams, players, MITRE ATT&CK patterns, kill chain phases, tags |
| Scenarios & simulations | create / update / launch scenarios, manage simulations, manage injects and expectations, scenario variables |
| Testing & payloads | create and manage payloads, create / launch / relaunch atomic tests |
| Findings & vulnerabilities | create and manage findings, manage vulnerabilities (CVE (Common Vulnerabilities and Exposures) + CVSS (Common Vulnerability Scoring System)) |
| Assets & content | manage endpoints and asset groups, documents, media channels, articles, challenges |
| Lessons & reports | lessons-learned templates and answers, simulation reports |

Platform administration is deliberately out of scope: no user, group, role, organization, security platform, taxonomy, connector or platform settings management is available through the MCP server.

## Client configuration

In XTM One, open **My Profile > MCP Endpoint** to find the endpoint URL, the live connection status, and a ready-to-copy configuration. The configuration for Cursor (`.cursor/mcp.json`) or Claude Desktop (`claude_desktop_config.json`) looks like this:

```json
{
  "mcpServers": {
    "openaev": {
      "url": "https://<your-xtm-one>/mcp/openaev",
      "headers": {
        "Authorization": "Bearer fcp-..."
      }
    }
  }
}
```

You can verify the connection from a terminal:

```bash
curl -s -X POST https://<your-xtm-one>/mcp/openaev \
  -H "Authorization: Bearer fcp-..." \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": 1, "method": "tools/list"}'
```

## Administration

Administrators can disable the MCP servers globally in XTM One under **Settings > MCP Endpoint** (Platform MCP Servers toggle). When disabled, the endpoint returns `403` for all clients.

| Response | Meaning |
| -------- | ------- |
| `401` | Missing or invalid API key / token |
| `403` | The platform MCP servers are disabled in XTM One settings |
| `404` | No OpenAEV platform is registered with XTM One |

!!! note "Full documentation"

    For more details about MCP endpoints, API keys and platform administration, please refer to the [XTM One documentation](https://docs.xtmone.io/).
