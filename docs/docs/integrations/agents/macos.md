# Install the OpenAEV agent on macOS

This page lists the macOS requirements, the installation modes and how to uninstall the agent. Get the install command from the **Install simulation agents** page (see [OpenAEV agent](openaev-agent.md#installation)). "Best effort" support levels are explained in [Best effort support](openaev-agent.md#best-effort-support).

## Operating system & architecture

| Architecture              | Support level   | Notes           |
|---------------------------|-----------------|-----------------|
| **ARM64 (Apple Silicon)** | ✅ Supported     |                 |
| **x86_64 (Intel)**        | ⚠️ Best effort  | Limited testing |
| 32-bit architectures      | ❌ Not supported |                 |
| Other CPU architectures   | ❌ Not supported |                 |

## Runtime & tooling

* **launchd must be available and running**
* **curl** must be available
* **openssl** must be available, the installer uses it to [verify the agent signature](openaev-agent.md#signed-agents)
* TLS support must be enabled
* The system must allow:

    * Execution of downloaded binaries
    * Write and execute permissions in the installation directory

## Privileges & security

* Installation and execution require **administrator privileges**

!!! warning

    Temporary limitation: on macOS, **Standard installation (session)** and **Advanced installation as User (service)** are currently unavailable.
    Use **Advanced installation as System (service)**.

## Installation mode

| Installation mode                             | Installation                                                                                                                                            | Execution agent and Threat Arsenal Action                                                          | Verification/Start/Stop agent                                                                                                                                                                                                               | Folder path                                 | AV exclusions                                          |
|:----------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------|:-------------------------------------------------------|
| **Advanced installation as System (service)** | Terminal with sudo privileges                                                                                                                           | Background, as soon as the machine powers on, with the root privilege and environment | `launchctl enable system/io.filigran.openaev-agent`<br/>`launchctl bootstrap system /Library/LaunchDaemons/io.filigran.openaev-agent.plist`<br/>`launchctl bootout system /Library/LaunchDaemons/io.filigran.openaev-agent.plist`                                             | `/opt/openaev-agent`                        | `/opt/openaev-agent/runtimes`                          |

## Uninstall the agent

Only **Advanced installation as System (service)** is available on macOS:

```bash
sudo launchctl bootout system /Library/LaunchDaemons/io.filigran.openaev-agent.plist
sudo rm -f /Library/LaunchDaemons/io.filigran.openaev-agent.plist
sudo rm -rf /opt/openaev-agent
```

## What's next?

- [OpenAEV agent](openaev-agent.md) -- Features, proxy, security and troubleshooting
- [Remove the Endpoint from OpenAEV](openaev-agent.md#remove-the-endpoint-from-openaev) -- Clean up after uninstalling
