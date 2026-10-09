# XTM One

XTM One is the AI platform of the Filigran eXtended Threat Management (XTM) suite. When OpenAEV is registered with XTM One, it unlocks the AI features of OpenAEV. See the [XTM One documentation](https://docs.xtmone.io/) to deploy XTM One.

## What it enables

- **Enterprise Edition**: an XTM license on XTM One can grant Enterprise Edition to OpenAEV. See [Enterprise Edition](../../administration/enterprise.md).
- **Ask Ariane**: the AI assistant in the top bar, and the **CTEM Command Center** link.
- **Ask AI**: generate or improve text in some form fields.
- **Scenario assistant**: generate TTPs from a threat report or a file. See [Scenario assistant](../../usage/build/scenario/scenario.md#scenario-assistant).
- **Detection rules**: generate detection rules from an executed Inject. See [Inject result](../../usage/run-and-evaluate/injects/inject-result.md).
- **Phishing**: generate email templates with AI. See [Email templates](../../usage/build/components/phishing/email-templates.md).
- **Autonomous attack**: the orchestrator of autonomous attack chaining runs in XTM One. See [Autonomous attack](../../usage/attack-chaining/autonomous/overview.md).
- **MCP server**: use OpenAEV from MCP clients. See [MCP server](../mcp-server/mcp-server.md).

## Connect OpenAEV to XTM One

1. Set the XTM One URL and the registration token defined in XTM One in the OpenAEV configuration:

    | Parameter             | Environment variable  | Description                                                                 |
    |:----------------------|:----------------------|:----------------------------------------------------------------------------|
    | openaev.xtm.one.url   | OPENAEV_XTM_ONE_URL   | XTM One URL, as reachable from OpenAEV (an internal address works)          |
    | openaev.xtm.one.token | OPENAEV_XTM_ONE_TOKEN | XTM One registration token                                                  |

2. If XTM One reaches OpenAEV on another address than its public URL, set `OPENAEV_API_URL` on the XTM One side.
3. Restart OpenAEV. It registers with XTM One at startup, then every 5 minutes.
4. Check that the **Ask Ariane** button appears in the top bar. Most AI features also need Enterprise Edition.

See [Configuration](../../reference/deployment/configuration.md#xtm-suite-xtm-one) for how the two platforms authenticate each other.

## What's next?

- [OpenCTI](xtm-suite-connector.md) -- Connect OpenAEV to OpenCTI
- [MCP server](../mcp-server/mcp-server.md) -- Use OpenAEV from MCP clients through XTM One
- [Deploy Collectors](../collectors/deploy-collectors.md) -- The XTM One Collector imports XTM One agents as AI targets
- [XTM One documentation](https://docs.xtmone.io/) -- Deploy and configure XTM One
