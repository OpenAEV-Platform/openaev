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

### How it works

1. The installer writes the platform URL and the other settings to `openaev-agent-config.toml`. The token is **not**
   in this file.
2. The installer writes the token to a temporary file, `openaev-agent-token.bootstrap`, and creates an empty
   `openaev-agent-token.dpapi` file. Both files are only readable by the accounts listed below.
3. At its first start, the agent encrypts the token into `openaev-agent-token.dpapi`, checks that it can decrypt it
   back, then deletes the bootstrap file.
4. Each time the agent contacts OpenAEV, it decrypts the token, uses it, then wipes it from memory.

If the agent cannot encrypt the token (step 3), it logs an error, keeps the bootstrap file and keeps working with it.
It tries again at its next start.

### Files in the installation folder

| File                              | Content                            | Lifetime                         |
|:----------------------------------|:-----------------------------------|:---------------------------------|
| `openaev-agent-config.toml`       | Settings, without the token        | Permanent                        |
| `openaev-agent-token.bootstrap`   | Token in clear text                | Deleted at the agent first start |
| `openaev-agent-token.dpapi`       | Token encrypted with DPAPI         | Permanent                        |

### Who can access the token files

| Installation mode                             | Agent account     | Access to the token files                                                    |
|:----------------------------------------------|:------------------|:-----------------------------------------------------------------------------|
| **Standard installation (session)**           | Logged-in user    | SYSTEM, Administrators and the user                                          |
| **Advanced installation as User (service)**   | Service account   | SYSTEM, Administrators, and the service account (read, write or delete only) |
| **Advanced installation as System (service)** | SYSTEM            | SYSTEM and Administrators                                                    |

To check that the token is protected, run in PowerShell as administrator:

```powershell
icacls "[FOLDER_PATH]\openaev-agent-token.dpapi"
Test-Path "[FOLDER_PATH]\openaev-agent-token.bootstrap"
Select-String "[FOLDER_PATH]\openaev-agent-config.toml" -Pattern '^\s*token\s*='
```

The expected result: only the accounts of the table above appear, with no `(I)` (inherited permissions), `Test-Path`
returns `False`, and `Select-String` returns nothing.

!!! warning "Limits of DPAPI"

    * Administrators of the machine can still get the token, for example by running a program as SYSTEM.
    * Any program that runs as the agent account can decrypt the token, including the Payloads the agent executes.
    * A full copy of the disk can be decrypted offline. Encrypt the disk, for example with BitLocker.
    * If an administrator **resets** the password of a local account used by an **Advanced installation as User
      (service)**, the account loses its DPAPI key and the agent can no longer decrypt its token. Reinstall the agent.
      Changing the password from the account itself keeps the key.

### Token storage messages in the log

| Message in `openaev-agent.log`                                    | Meaning                                                                                      | Action                                         |
|:------------------------------------------------------------------|:---------------------------------------------------------------------------------------------|:-----------------------------------------------|
| `Token moved to the OS secret store`                              | The token is encrypted and the clear-text copies are deleted                                 | None                                           |
| `Could not move the token to the OS secret store: ...`            | The agent could not encrypt the token. It keeps using the clear-text copy                    | Check the cause in the message, then restart   |
| `The token is still in clear text in ...openaev-agent-config.toml` | The token is encrypted, but the agent cannot remove it from the config file                  | Reinstall the agent                            |
| `no token in ...openaev-agent-token.dpapi, reinstall the agent`    | No token was ever encrypted, and no bootstrap file is left                                   | Reinstall the agent                            |

The agent also goes offline if `openaev-agent-token.dpapi` is damaged, or if the agent account can no longer decrypt
it. In both cases, reinstall the agent.

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
