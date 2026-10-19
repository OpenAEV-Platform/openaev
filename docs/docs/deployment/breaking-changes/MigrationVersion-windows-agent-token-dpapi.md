# Windows agent token encrypted with DPAPI

!!! info ""

    * **Introduced in**: `OpenAEV [MigrationVersion]`

## Description of changes

On Windows, the agent no longer stores its token in clear text in `openaev-agent-config.toml`. It encrypts the token
with **DPAPI (Data Protection API)** into `openaev-agent-token.dpapi`, in the installation folder. Only the account the
agent runs as can decrypt it.

The installer writes the token to a temporary file, `openaev-agent-token.bootstrap`. At its first start, the agent
encrypts the token, checks that it can decrypt it back, then deletes the temporary file. See
[Token storage](../../integrations/agents/windows.md#token-storage) for details.

Linux and macOS agents are not affected.

## Impact

| Installation mode                             | Upgrade from the platform | Result                                                                                       |
|:----------------------------------------------|:--------------------------|:---------------------------------------------------------------------------------------------|
| **Standard installation (session)**           | Runs the new installer    | Token encrypted automatically                                                                |
| **Advanced installation as System (service)** | Runs the new installer    | Token encrypted automatically                                                                |
| **Advanced installation as User (service)**   | Replaces the binary only  | The agent tries to encrypt the token. The clear-text copy often stays in the config file     |

Other impacts:

* Scripts or tools that read the token from `openaev-agent-config.toml` no longer find it.
* An older agent cannot read the encrypted token. Going back to an older version requires a reinstall.

## Migration guide

After upgrading, for each **Advanced installation as User (service)**:

1. **Reinstall the agent from the platform**.
2. Check that the token is no longer in clear text:

    ```powershell
    Select-String "<install folder>\openaev-agent-config.toml" -Pattern '^\s*token\s*='
    Test-Path "<install folder>\openaev-agent-token.bootstrap"
    ```

    `Select-String` returns nothing and `Test-Path` returns `False`.

3. Check `openaev-agent.log`: it contains `Token moved to the OS secret store`, and no
   `The token is still in clear text` warning.

!!! warning

    If the token was readable in clear text before, treat it as exposed: encrypting it does not invalidate a copied
    token. To issue a new one, see [Rotating the installer token](../../integrations/agents/openaev-agent.md#rotating-the-installer-token).

## What's next?

- [Token storage on Windows](../../integrations/agents/windows.md#token-storage) -- How the agent protects its token
- [Breaking changes](../breaking-changes.md) -- All breaking changes per version
