# Install the OpenAEV agent on Linux

This page lists the Linux requirements, the installation modes and how to uninstall the agent. Get the install command from the **Install simulation agents** page (see [OpenAEV agent](openaev-agent.md#installation)). "Best effort" support levels are explained in [Best effort support](openaev-agent.md#best-effort-support).

## Operating system & architecture

| Architecture            | Support level      | Notes   |
|-------------------------|--------------------|---------|
| **x86_64**              | ✅ Supported        |         |
| **ARM64**               | ✅ Supported        |         |
| 32-bit architectures    | ❌ Not supported    |         |
| Other CPU architectures | ❌ Not supported    |         |

| Distribution type                                      | Support level     | Notes                       |
|--------------------------------------------------------|-------------------|-----------------------------|
| **systemd-based distributions** (Debian, Ubuntu, etc.) | ✅ Supported       | systemd required            |
| Non-systemd distributions                              | ❌ Not supported   | Installer relies on systemd |

## Runtime & tooling

* **systemd must be installed and running**
* **curl** must be available
* **openssl** must be available, the installer uses it to [verify the agent signature](openaev-agent.md#signed-agents)
* TLS support must be enabled

## Privileges & security

* The advanced (service) installations require **root or sudo privileges**
* User-based services require permission to manage `systemctl --user`

## Installation mode

| Installation mode                             | Installation                                                                                                                                            | Execution agent and Threat Arsenal Action                                                          | Verification/Start/Stop agent                                                                                                                        | Folder path                                 | AV exclusions                                        |
|:----------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------|:-----------------------------------------------------|
| **Standard installation (session)**           | Asset with GUI and terminal with standard privileges for the logged-in user                                                                             | Background, only when user is logged in, with the user privilege and environment      | `systemctl --user enable openaev-agent-session`<br/>`systemctl --user start openaev-agent-session`<br/>`systemctl --user stop openaev-agent-session` | `$HOME/.local/openaev-agent-session`        | `$HOME/.local/openaev-agent-session/runtimes `       |
| **Advanced installation as User (service)**   | Terminal with sudo privileges, replace params [USER] and [GROUP] in the bash<br/>snippet and in the following commands by the username and group wanted | Background, as soon as the machine powers on, with the user privilege and environment | `systemctl enable [USER]-openaev-agent`<br/>`systemctl start [USER]-openaev-agent`<br/>`systemctl stop [USER]-openaev-agent`                         | `~[USER]/.local/openaev-agent-service-[USER]` | `~[USER]/.local/openaev-agent-service-[USER]/runtimes` |
| **Advanced installation as System (service)** | Terminal with sudo privileges                                                                                                                           | Background, as soon as the machine powers on, with the root privilege and environment | `systemctl enable openaev-agent`<br/>`systemctl start openaev-agent`<br/>`systemctl stop openaev-agent`                                              | `/opt/openaev-agent`                        | `/opt/openaev-agent/runtimes`                        |

!!! note

    On Linux and macOS, to run command Threat Arsenal Actions without sudo password prompts, see
    [this tutorial](https://gcore.com/learning/how-to-disable-password-for-sudo-command/).

## Uninstall the agent

**Standard installation (session)**:

```bash
systemctl --user stop openaev-agent-session
systemctl --user disable openaev-agent-session
systemctl --user daemon-reload
systemctl --user reset-failed
rm -rf $HOME/.local/openaev-agent-session
```

**Advanced installation as User (service)**, replace `[USER]` with the username:

```bash
sudo systemctl stop [USER]-openaev-agent
sudo systemctl disable [USER]-openaev-agent
sudo systemctl daemon-reload
sudo systemctl reset-failed
sudo rm -rf ~[USER]/.local/openaev-agent-service-[USER]
```

**Advanced installation as System (service)**:

```bash
sudo systemctl stop openaev-agent
sudo systemctl disable openaev-agent
sudo systemctl daemon-reload
sudo systemctl reset-failed
sudo rm -rf /opt/openaev-agent
```

## What's next?

- [OpenAEV agent](openaev-agent.md) -- Features, proxy, security and troubleshooting
- [Remove the Endpoint from OpenAEV](openaev-agent.md#remove-the-endpoint-from-openaev) -- Clean up after uninstalling
