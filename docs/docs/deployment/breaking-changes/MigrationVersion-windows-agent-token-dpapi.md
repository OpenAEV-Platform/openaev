# Windows agent token encrypted with DPAPI

!!! info ""

    * **Introduced in**: `OpenAEV [MigrationVersion]`

## Description of changes

On Windows, the agent no longer keeps its token in clear text in `openaev-agent-config.toml`. When it starts and finds
a token in the config file, it encrypts the token with **DPAPI (Data Protection API)** into
`openaev-agent-token.dpapi`, in the installation folder, then removes the token from the config file. Only the account
the agent runs as can decrypt it. If any of these steps fails, the agent stops. See
[Token storage](../../integrations/agents/windows.md#token-storage) for details.

Linux and macOS agents are not affected for now.

## Impact

| Installation mode                             | Automatic upgrade from the platform | Result                                                                                  |
|:----------------------------------------------|:------------------------------------|:----------------------------------------------------------------------------------------|
| **Standard installation (session)**           | Runs the new installer              | Token encrypted automatically                                                           |
| **Advanced installation as System (service)** | Runs the new installer              | Token encrypted automatically                                                           |
| **Advanced installation as User (service)**   | Replaces the binary only            | The agent cannot remove the token from its config file and stops until you reinstall it |

Other impacts:

* Scripts or tools that read the token from `openaev-agent-config.toml` no longer find it.
* An older agent cannot read the encrypted token. Going back to an older version requires a reinstall.

## Migration guide

Nothing to do for the **Standard installation (session)** and the **Advanced installation as System (service)**.

For each **Advanced installation as User (service)**, reinstall the agent from the platform once after upgrading.
Until then, the agent logs `The token is still in clear text` and stops at each start, and the Endpoint shows as
inactive.

!!! warning

    If the token was readable in clear text before, treat it as exposed: encrypting it does not invalidate a copied
    token. To issue a new one, see [Rotating the installer token](../../integrations/agents/openaev-agent.md#rotating-the-installer-token).

## What's next?

- [Token storage on Windows](../../integrations/agents/windows.md#token-storage) -- How the agent protects its token
- [Breaking changes](../breaking-changes.md) -- All breaking changes per version
