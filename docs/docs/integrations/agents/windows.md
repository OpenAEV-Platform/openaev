# Install the OpenAEV agent on Windows

This page lists the Windows requirements, the installation modes, how the agent protects its token and how to uninstall the agent. Get the install command from the **Install simulation agents** page (see [OpenAEV agent](openaev-agent.md#installation)). "Best effort" support levels are explained in [Best effort support](openaev-agent.md#best-effort-support).

## Operating system & architecture

| Architecture  | Support level     |
|---------------|-------------------|
| **x64**       | ✅ Supported       |
| **ARM64**     | ✅ Supported       |
| x86 (32-bit)  | ❌ Not supported   |
| ARM32         | ❌ Not supported   |

| Operating system              | Support level        | Notes               |
|-------------------------------|----------------------|---------------------|
| **Windows 10**                | ✅ Supported          |                     |
| **Windows 11**                | ✅ Supported          |                     |
| **Windows Server 2019**       | ✅ Supported          |                     |
| **Windows Server 2022**       | ✅ Supported          |                     |
| Windows 8 / 8.1               | ⚠️ Best effort       | No guaranteed fixes |
| Windows Server 2016           | ⚠️ Best effort       | No guaranteed fixes |
| Windows Server 2012 R2        | ⚠️ Best effort       | No guaranteed fixes |
| Windows 7 and earlier         | ❌ Not supported      |                     |
| Windows Server 2008 / 2008 R2 | ❌ Not supported      |                     |


## Runtime & tooling

!!! note

    If the installation fails, try using PowerShell 7 or higher.

* TLS 1.2 or higher must be available
* The system `Path` environment variable must include: `%SYSTEMROOT%\System32\` and `%SYSTEMROOT%\System32\WindowsPowerShell\v1.0\`

## Privileges & security

* The advanced (service) installations require **local administrator privileges**
* For **Advanced installation as User (service)**:

    * The target user must have the **“Log on as a service”** policy enabled
    * See:
      [https://learn.microsoft.com/en-us/system-center/scsm/enable-service-log-on-sm](https://learn.microsoft.com/en-us/system-center/scsm/enable-service-log-on-sm)

## Antivirus

* Antivirus exclusions are mandatory and must apply **only** to the `runtimes` directory

## Installation mode

*[UserSanitized] in the table below means username without special character like "\", "/",...*

| Installation mode                             | Installation                                                                                                                                                                                                                 | Execution agent and Threat Arsenal Action                                                                                   | Verification/Start/Stop agent                                                                                                                                                                                                                                                | Folder path                                                                                                                    | AV exclusions                                                                                                                                    |
|:----------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------|
| **Standard installation (session)**           | Asset with GUI and terminal with standard privileges or admin privileges for the logged-in user                                                                                                                              | Background, only when user is logged in, with the user privilege from the powershell elevation and environment | `Get-Process openaev-agent \| Where-Object { $_.Path -eq "[FOLDER_PATH]\openaev-agent.exe" }`<br/>`Get-Process openaev-agent \| Where-Object { $_.Path -eq "[FOLDER_PATH]\openaev-agent.exe" } \| Stop-Process -Force`<br/>`Start-Process "[FOLDER_PATH]\openaev-agent.exe"` | `$HOME\.openaev\OAEVAgent-Session-[UserSanitized]`<br/>OR<br/>`$HOME\.openaev\OAEVAgent-Session-Administrator-[UserSanitized]` | `$HOME\.openaev\OAEVAgent-Session-[UserSanitized]\runtimes`<br/>OR<br/>`$HOME\.openaev\OAEVAgent-Session-Administrator-[UserSanitized]\runtimes` |
| **Advanced installation as User (service)**   | Enable the "Service Logon" policy (see above)<br/>Terminal with admin privileges, replace `-User USER -Password PASSWORD` in the<br/>PowerShell snippet by the username with domain and password wanted | Background, as soon as the machine powers on, with the user privilege and environment                          | `Get-Service -Name "OAEVAgent-Service-[UserSanitized]"`<br/>`Start-Service -Name "OAEVAgent-Service-[UserSanitized]"`<br/>`Stop-Service -Name "OAEVAgent-Service-[UserSanitized]"`                                                                                           | `$HOME\.openaev\OAEVAgent-Service-[UserSanitized]`                                                                             | `$HOME\.openaev\OAEVAgent-Service-[UserSanitized]\runtimes`                                                                                      |
| **Advanced installation as System (service)** | Terminal with admin privileges for the authority system user                                                                                                                                                                 | Background, as soon as the machine powers on, with the SYSTEM privilege and environment                        | `Get-Service -Name "OAEVAgentService"`<br/>`Start-Service -Name "OAEVAgentService"`<br/>`Stop-Service -Name "OAEVAgentService"`                                                                                                                                              | `C:\Program Files (x86)\Filigran\OAEV Agent`                                                                                   | `C:\Program Files (x86)\Filigran\OAEV Agent\runtimes`                                                                                            |

## Token storage

The agent uses a token to authenticate to OpenAEV. On Windows, the agent does not keep this token in clear text: it
encrypts it with **DPAPI (Data Protection API)**, the Windows service that encrypts data with a key that belongs to a
Windows account. Only the account the agent runs as can decrypt the token.

DPAPI is built into Windows since Windows XP and Windows Server 2003, so it is available on every Windows version the
agent supports, with nothing to install (see
[CryptProtectData](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata) in the
Microsoft documentation).

### How it works

1. The installer writes the token and the other settings to `openaev-agent-config.toml`, as before. A reinstall also
   deletes the previous `openaev-agent-token.dpapi`.
2. When the agent starts and finds a token in `openaev-agent-config.toml`, it encrypts the token into
   `openaev-agent-token.dpapi`, then removes the token from `openaev-agent-config.toml`. It then reads the token back
   once to check it. If any of these steps fails, the agent logs the error and stops.
3. Each time the agent contacts OpenAEV, it decrypts the token, uses it, then wipes it from memory.

The agent checks the config file at each start, so a new token written by an upgrade or a reinstall replaces the
encrypted one.

### Files in the installation folder

| File                          | Content                                                 |
|:------------------------------|:--------------------------------------------------------|
| `openaev-agent-config.toml`   | Settings. The token only until the agent first starts   |
| `openaev-agent-token.dpapi`   | Token encrypted with DPAPI                              |

### Who can access the token file

| Installation mode                             | Agent account     | Access to `openaev-agent-token.dpapi`             |
|:----------------------------------------------|:------------------|:--------------------------------------------------|
| **Standard installation (session)**           | Logged-in user    | SYSTEM, Administrators and the user               |
| **Advanced installation as User (service)**   | Service account   | SYSTEM, Administrators and the service account    |
| **Advanced installation as System (service)** | SYSTEM            | SYSTEM and Administrators                         |

To check that the token is protected, run in PowerShell as administrator:

```powershell
icacls "[FOLDER_PATH]\openaev-agent-token.dpapi"
Select-String "[FOLDER_PATH]\openaev-agent-config.toml" -Pattern '^\s*token\s*='
```

The expected result: `icacls` lists only `NT AUTHORITY\SYSTEM`, `BUILTIN\Administrators` and `OWNER RIGHTS` (the
account the agent runs as, which created the file), with no `(I)` (inherited permissions), and `Select-String` returns
nothing.

!!! warning "Limits of DPAPI"

    * Administrators of the machine can still get the token, for example by running a program as SYSTEM.
    * Any program that runs as the agent account can decrypt the token, including the Payloads the agent executes.
    * A full copy of the disk can be decrypted offline. Encrypt the disk, for example with BitLocker.
    * If an administrator **resets** the password of a local account used by an **Advanced installation as User
      (service)**, the account loses its DPAPI key and the agent can no longer decrypt its token. Reinstall the agent.
      Changing the password from the account itself keeps the key.

### Token storage messages in the log

At start, each message below stops the agent. While the agent runs, a read error only blocks the request that needs
the token.

| Message in `openaev-agent.log`                                          | Meaning                                                                        | Action              |
|:------------------------------------------------------------------------|:-------------------------------------------------------------------------------|:--------------------|
| `Unable to save the token in the OS secret store: ...`                  | The agent could not encrypt the token                                          | Reinstall the agent |
| `The token is still in clear text in ...openaev-agent-config.toml: ...` | The token is encrypted, but the agent cannot remove it from the config file    | Reinstall the agent |
| `Unable to read the token from ...openaev-agent-token.dpapi: ...`       | The file is missing or damaged, or the agent account can no longer decrypt it  | Reinstall the agent |
| `The token in ...openaev-agent-token.dpapi is not text`                 | The file is damaged                                                            | Reinstall the agent |

## Uninstall the agent

1. For a **Standard installation (session)**, stop the agent:

    ```powershell
    Get-Process openaev-agent | Where-Object { $_.Path -eq "[FOLDER_PATH]\openaev-agent.exe" } | Stop-Process -Force
    ```

2. Run `uninstall.exe` from the installation folder (see the **Folder path** column above) and confirm.
   It stops and removes the service or the startup task, then deletes the folder, including the encrypted token file.
   For the service modes, run it as administrator.
3. For an **Advanced installation as User (service)**, disable the **Log on as a service** policy for the user if it no
   longer needs it.

## What's next?

- [OpenAEV agent](openaev-agent.md) -- Features, proxy, security and troubleshooting
- [Windows agent token encrypted with DPAPI](../../deployment/breaking-changes/MigrationVersion-windows-agent-token-dpapi.md) -- What changes on upgrade
- [Remove the Endpoint from OpenAEV](openaev-agent.md#remove-the-endpoint-from-openaev) -- Clean up after uninstalling
