# Executors

## Introduction

To be able to use the power of the OpenAEV platform on endpoints, you need at least one **neutral executor** that will
be in charge of executing implants as detached processes. Implants will then execute threat arsenal actions.

The platform manages different executors which can be installed on Windows, Linux and MacOS using x86_64 or arm64
architectures. This table below summarizes the information about each agent.

| Executor                           | Type          | Installation mode                                 | Installation type | Run As                                 | Threat arsenal action execution                | Multi agents for an endpoint                     |
|:-----------------------------------|:--------------|:--------------------------------------------------|:------------------|:---------------------------------------|:-----------------------------------------------|:-------------------------------------------------|
| **OpenAEV Agent (native/default)** | Open source   | As a user session, user service or system service | Script            | A standard or admin background process | As a user standard, user admin or system admin | Yes, depending on the user and installation mode |
| **Tanium Agent**                   | Under license | As a system service                               | Executable        | An admin background process            | As a system admin                              | No, always the same agent                        |                              
| **CrowdStrike Falcon Agent**       | Under license | As a system service                               | Executable        | An admin background process            | As a system admin                              | No, always the same agent                        |                              
| **SentinelOne Agent**              | Under license | As a system service                               | Executable        | An admin background process            | As a system admin                              | No, always the same agent                        |
| **Palo Alto Cortex Agent**         | Under license | As a system service                               | Executable        | An admin background process            | As a system admin                              | No, always the same agent                        |
| **Caldera Agent**                  | Open source   | As a user session                                 | Script            | An admin background process            | As a user admin                                | Yes, depending on the user                       |                      

## OpenAEV Agent

The OpenAEV agent is available for Windows, Linux and MacOS, it is the native / default way to execute implants and
threat arsenal actions on endpoints.

[Learn More](../../usage/openaev-agent.md)

---
<a id="tanium-agent"></a>

## Tanium Agent

The Tanium agent can be leveraged to execute implants as detached processes that will then execute threat arsenal
actions, according
to the [OpenAEV architecture](https://docs.openaev.io/latest/deployment/platform/overview/).

### Configure the Tanium Platform

We
provide [two Tanium packages](https://github.com/OpenAEV-Platform/openaev/blob/master/openaev-api/src/main/java/io/openaev/executors/tanium/openaev-tanium-packages.json)
to be imported into the Tanium platform.

![Tanium Packages](../assets/tanium-packages.png)

!!! warning "Tanium package configuration"

    Because OpenAEV should run implants as detached processes, you must uncheck  
    **"Launch this package command in a process group"** in the package configuration:

    ![Tanium Package](../assets/tanium-package.png)

!!! warning "Tanium Threat Response usage"

    If your environment uses **Tanium Threat Response (TTR)** together with the Tanium agent, you should rely on the **dedicated TTR package**.  
    This package technically works in all cases, but it is **only recommended** when OpenAEV runs on endpoints with TTR enabled.  
    Reason: this package performs more extensive operations on the machine and can generate **more noise and alerts**.  
    → If you do **not** use Tanium Threat Response, prefer the **standard Tanium package**.  

    📦 Packages to import:  
    - [OpenAEV Tanium Windows & Unix package (TTR)](https://github.com/OpenAEV-Platform/openaev/blob/master/openaev-api/src/main/java/io/openaev/executors/tanium/openaev-tanium-packages-TTR.json)  

    📜 Scripts to attach in the package configuration into files section:   
    - [Windows TTR script](https://github.com/OpenAEV-Platform/openaev/blob/master/openaev-api/src/main/java/io/openaev/executors/tanium/openaev-ttr.ps1)  

| Package type                | Recommended use case                  | Characteristics                                            |
|-----------------------------|---------------------------------------|------------------------------------------------------------|
| **Standard Tanium package** | Default use with Tanium agent only    | Lightweight, minimal impact, recommended in most scenarios |
| **TTR package**             | Tanium agent + Tanium Threat Response | Enables additional operations, may generate more noise     |

Once configured and imported, retrieve the package IDs from the URL:  
`ui/console/packages/XXXXX/preview`.

> ℹ️ Common group IDs in Tanium:
>
> - **Computer Group ID**: identifies which endpoints will be queried.
> - **Action Group ID**: identifies where actions (like package execution) are allowed.

### Configure the OpenAEV Platform

To configure the Tanium executor, navigate to the **Integrations > Executors** section in the OpenAEV menu and fill in the Tanium integration settings directly from the UI.

!!! note "Tanium API Key"

    The Tanium API key must have permissions to:  
    - Retrieve the endpoint list from the Tanium GraphQL API  
    - Launch packages on endpoints  

!!! info "Migrating from environment variables"

    If you previously configured this executor using environment variables or platform properties, these values have been **automatically migrated** to the database on first startup. After migration, changes to environment variables or properties are no longer taken into account — all configuration is now managed through the UI.

---

### Checks

Once enabled, you should see **Tanium** available in the `Install agents` section:

![Agents](../assets/agents.png)

Endpoints from the selected computer groups should now appear in the **OpenAEV Endpoints** section:

![Endpoints](../assets/tanium-endpoints.png)

!!! note "Agent uniqueness"

    An endpoint can only have **one Tanium agent** registered due to MAC address uniqueness.  
    Installing a new agent will overwrite the existing one, and you will always see a single endpoint in the OpenAEV console.

!!! success "Installation done"

    You are now ready to leverage your Tanium platform to run OpenAEV threat arsenal actions!

---
<a id="crowdstrike-falcon-agent"></a>

## CrowdStrike Falcon Agent

The CrowdStrike Falcon agent can be leveraged to execute implants as detached processes that will then execute threat
arsenal actions
according to the [OpenAEV architecture](https://docs.openaev.io/latest/deployment/platform/overview/).

### Configure the CrowdStrike Platform

#### Upload OpenAEV scripts

First of all, you need to create two custom scripts, one for Windows and one for Unix, covering both Linux and MacOS
systems.

To create it, go to `Host setup and management` > `Response and containment` > `Response scripts and files`. The names
of the scripts can be changed if necessary, they will be put in the OpenAEV configuration.

*Unix Script*

| Attribute             | Value                                                            |
|:----------------------|:-----------------------------------------------------------------|
| name                  | OpenAEV Subprocessor (Unix)                                      |
| shell type            | bash                                                             |
| script access         | Users with the role of RTR Administrator or RTR Active Responder |
| shared with workflows | yes                                                              |

Put the following script:

```bash
command=`echo $1 | grep -o '"command":"[^"]*' | grep -o '[^"]*$'`
echo $command | base64 -d | sh
```

Put the following Input schema:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "properties": {
    "command": {
      "type": "string"
    }
  },
  "required": [
    "command"
  ],
  "type": "object",
  "description": "This generated schema may need tweaking. In particular format fields are attempts at matching workflow field types but may not be correct."
}
```

![CrowdStrike unix script](../assets/crowdstrike-unix-script.png)

*Windows script*

| Attribute             | Value                                                            |
|:----------------------|:-----------------------------------------------------------------|
| name                  | OpenAEV Subprocessor (Windows)                                   |
| shell type            | PowerShell                                                       |
| script access         | Users with the role of RTR Administrator or RTR Active Responder |
| shared with workflows | yes                                                              |

Put the following script:

```PowerShell
$command = $args[0] | ConvertFrom-Json | Select -ExpandProperty 'command';
cmd.exe /d /c powershell.exe -ExecutionPolicy Bypass -WindowStyle Hidden -NonInteractive -NoProfile -encodedCommand $command
```

Put the following Input schema:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "properties": {
    "command": {
      "type": "string"
    }
  },
  "required": [
    "command"
  ],
  "type": "object",
  "description": "This generated schema may need tweaking. In particular format fields are attempts at matching workflow field types but may not be correct."
}
```

![CrowdStrike windows script](../assets/crowdstrike-windows-script.png)

Once created, your RTR scripts should have something like this:

![CrowdStrike RTR script](../assets/crowdstrike-scripts.png)

#### Create a host group with your targeted assets

To create a host group, go to `Host setup and management` > `Host groups`.

#### Create/Update response policies for your targeted platforms

As OpenAEV will ask CrowdStrike to create implants in order to execute threat arsenal actions as scripts, you need to
allow the
execution of custom scripts on your assets. To do so, you need to create a new response policy or update an existing one
for your assets' platforms.

To create or update a response policy, go to `Host setup and management` > `Response policies`.

There, choose a platform in the top left selector, then click on `Create policy` or click on the name of an existing
one.
![CrowdStrike Response Policies](../assets/crowdstrike-windows-policies.png)

The CrowdStrike UI should present you with a screen like this:
![CrowdStrike Response Policies Details](../assets/crowdstrike-windows-policy.png)

On this screen, click to allow `Custom Scripts` execution.
If an option named `Falcon Scripts` exists, allow it as well.
For the other options, you can choose to allow or deny them according to your security policy and what you want to test.
Click on `Save` to save your changes.

Finally, click on the `Assigned host groups` tab to add your previously created group to this policy.
Once done, the policy may take a few minutes to be applied to your assets.
You can go back to the policies list screen and check that there is a 0 in the `Pending` column to know that it has been
applied.

### Configure the OpenAEV platform

!!! warning "CrowdStrike API Key"

    Please note that the CrowdStrike API key should have the following permissions: API integrations, Hosts, Host groups, Real time response.

To configure the CrowdStrike executor, navigate to the **Integrations > Executors** section in the OpenAEV menu and fill in the CrowdStrike integration settings directly from the UI.

!!! info "Migrating from environment variables"

    If you previously configured this executor using environment variables or platform properties, these values have been **automatically migrated** to the database on first startup. After migration, changes to environment variables or properties are no longer taken into account — all configuration is now managed through the UI.

### Checks

Once enabled, you should see CrowdStrike available in your `Install agents` section

![CrowdStrike available agent](../assets/agents.png)

Also, the assets and the asset groups in the selected computer groups should now be available in the endpoints and asset
groups sections in OpenAEV:

![CrowdStrike Endpoints](../assets/crowdstrike-endpoints.png)

NB : An Asset can only have one CrowdStrike agent installed due to the uniqueness of the MAC address parameters. If you
try to install again a CrowdStrike agent on a platform, it will overwrite the actual one and you will always see one
Endpoint on the OpenAEV endpoint page.

!!! success "Installation done"

    You are now ready to leverage your CrowdStrike platform to run OpenAEV threat arsenal actions!

---
<a id="paloaltocortex-agent"></a>

## Palo Alto Cortex Agent

The Palo Alto Cortex agent can be leveraged to execute implants as detached processes that will then execute threat
arsenal actions
according to the [OpenAEV architecture](https://docs.openaev.io/latest/deployment/platform/overview/).

On Windows, because Palo Alto Cortex whitelists its own process tree, OpenAEV creates a scheduled task to detach the
process that will execute the threat arsenal actions.

### Configure the Palo Alto Cortex Platform

#### Upload OpenAEV scripts

First of all, you need to create one custom script for Unix, covering both Linux and MacOS systems and another one for
Windows.

To create these scripts, go to `Investigation & responses` > `Action Center` > `Agent Script Library` > `+ New Script`.
The names
of the scripts can be changed if necessary, the ids will be put in the OpenAEV configuration.
To get the scripts IDs, it may be necessary to add the Script UID column to the scripts list view.

*Unix Script*

Upload the following Python script:

[Download](../assets/paloaltocortex_subprocessor_unix.py)

Put the following Input schema:

![Palo Alto Cortex unix script1](../assets/paloaltocortex-unix-script-general.png)
![Palo Alto Cortex unix script2](../assets/paloaltocortex-unix-script-inputs-outputs.png)

*Windows script*

Upload the following Python script:

[Download](../assets/paloaltocortex_subprocessor_windows.py)

Put the following Input schema:

![Palo Alto Cortex windows script1](../assets/paloaltocortex-windows-script-general.png)
![Palo Alto Cortex windows script2](../assets/paloaltocortex-windows-script-inputs-outputs.png)

#### Create a group with your targeted assets

To create a group, go to `Inventory` > `Endpoints` > `Groups`.

### Configure the OpenAEV platform

!!! warning "Palo Alto Cortex API Key"

    Please note that the Palo Alto Cortex API key created in "Settings/Configurations/API Keys" should have the following minimum role: "Instance Administrator" and security level: "Standard".

To configure the Palo Alto Cortex executor, navigate to the **Integrations > Executors** section in the OpenAEV menu and fill in the Palo Alto Cortex integration settings directly from the UI.

### Checks

Once enabled, you should see Palo Alto Cortex available in your `Install agents` section

![Palo Alto Cortex available agent](../assets/paloaltocortex-agents.png)

Also, the assets and the asset groups in the selected groups should now be available in the endpoints and asset
groups sections in OpenAEV:

![Palo Alto Cortex Endpoints](../assets/paloaltocortex-endpoints.png)

NB : An Asset can only have one Palo Alto Cortex agent installed due to the uniqueness of the MAC address parameters. If
you
try to install again a Palo Alto Cortex agent on a platform, it will overwrite the actual one and you will always see
one
Endpoint on the OpenAEV endpoint page.

!!! success "Installation done"

    You are now ready to leverage your Palo Alto Cortex platform to run OpenAEV threat arsenal actions!

---
<a id="sentinelone-agent"></a>

## SentinelOne Agent

The SentinelOne agent can be leveraged to execute implants as detached processes that will then execute threat arsenal
actions
according to the [OpenAEV architecture](https://docs.openaev.io/latest/deployment/platform/overview/).

!!! warning "SentinelOne"

    Please note that the SentinelOne license with add-on "remote script orchestration" is required to launch SentinelOne scripts with OpenAEV → see in SentinelOne/Settings/Configuration/Add-ons

### Configure the SentinelOne Platform

#### Upload OpenAEV scripts

First of all, you need to create two custom scripts, one for Windows and one for Unix, covering both Linux and MacOS
systems.

To create it, go to `Automation` > `Remote Ops` > `Create new`. The names
of the scripts can be changed if necessary, the ids will be put in the OpenAEV configuration.

*Unix Script*

Upload the following script (encoded for Unix):

[Download](../assets/sentinelone_subprocessor_unix.sh)

Put the following Input schema:

![SentinelOne unix script1](../assets/sentinelone-unix-script1.png)
![SentinelOne unix script2](../assets/sentinelone-unix-script2.png)

*Windows script*

Upload the following script (encoded for Windows):

[Download](../assets/sentinelone_subprocessor_windows.ps1)

Put the following Input schema:

![SentinelOne windows script1](../assets/sentinelone-windows-script1.png)
![SentinelOne windows script2](../assets/sentinelone-windows-script2.png)

Once created, your Remote Ops scripts should have something like this:

![SentinelOne RTR script](../assets/sentinelone-scripts.png)

#### Create a wrapper with your targeted assets

To create a wrapper (account/site/group), go to `Settings` > `Accounts/Sites`.

### Configure the OpenAEV platform

!!! warning "SentinelOne API Key"

    Please note that the SentinelOne API key created in "Settings/Users/Service Users" should have the following minimum role: "IR Team". The API key and the scripts must be created for and with the same user and the required account/site.

To configure the SentinelOne executor, navigate to the **Integrations > Executors** section in the OpenAEV menu and fill in the SentinelOne integration settings directly from the UI.

!!! info "Migrating from environment variables"

    If you previously configured this executor using environment variables or platform properties, these values have been **automatically migrated** to the database on first startup. After migration, changes to environment variables or properties are no longer taken into account — all configuration is now managed through the UI.

### Checks

Once enabled, you should see SentinelOne available in your `Install agents` section

![SentinelOne available agent](../assets/sentinelone-agents.png)

Also, the assets and the asset groups in the selected accounts/sites/groups should now be available in the endpoints and
asset
groups sections in OpenAEV:

![Sentinel Endpoints](../assets/sentinelone-endpoints.png)

NB : An Asset can only have one SentinelOne agent installed due to the uniqueness of the MAC address parameters. If you
try to install again a SentinelOne agent on a platform, it will overwrite the actual one and you will always see one
Endpoint on the OpenAEV endpoint page.

!!! success "Installation done"

    You are now ready to leverage your SentinelOne platform to run OpenAEV threat arsenal actions!

---

## Caldera Agent

The Caldera agent can be leveraged to execute implants as detached processes that will then execute threat arsenal
actions according to
the [OpenAEV architecture](https://docs.openaev.io/latest/deployment/platform/overview/).

!!! note "Caldera already installed"

    If you already have a working Caldera installation, just go directly to [OpenAEV configuration section](#openaev-configuration).

### Deploy Caldera

To deploy Caldera, you can just add Caldera to the OpenAEV stack, we advise you to modify your `docker-compose.yml` and
add a [Caldera service](https://github.com/OpenAEV-Platform/caldera/blob/filigran/docker/docker-compose.yml):

```
services:
  caldera:
    image: openaev/caldera-server:5.1.0
    restart: always
    ports:
      - "8888:8888"
    environment:
      CALDERA_URL: http://localhost:8888
    volumes:
      - type: bind
        source: caldera.yml
        target: /usr/src/app/conf/local.yml
```

As you can see in the configuration, you will also need a configuration
file [caldera.yml](https://github.com/OpenAEV-Platform/caldera/blob/filigran/docker/caldera.yml) because Caldera does
not support well environment variables for configuration.

Download [caldera.yml](https://github.com/OpenAEV-Platform/caldera/blob/filigran/docker/caldera.yml) and put it
alongside your `docker-compose.yml` file. This file must be modified prior launching, only change what is marked as *
*Change this**, listed below.

```yaml
users:
  red:
    red: ChangeMe                                                                     # Change this
  blue:
    blue: ChangeMe                                                                    # Change this
api_key_red: ChangeMe                                                                 # Change this
api_key_blue: ChangeMe                                                                # Change this
api_key: ChangeMe                                                                     # Change this
crypt_salt: ChangeMe                                                                  # Change this
encryption_key: ChangeMe                                                              # Change this
app.contact.http: http://caldera.myopenaev.myorganization.com:8888                    # Change this
app.contact.tunnel.ssh.user_password: ChangeMe                                        # Change this
```

Just update your stack and check Caldera is running:

```bash
docker compose up -d
```

### OpenAEV configuration

To configure the Caldera executor, navigate to the **Integrations > Executors** section in the OpenAEV menu and fill in the Caldera integration settings directly from the UI.

!!! info "Migrating from environment variables"

    If you previously configured this executor using environment variables or platform properties, these values have been **automatically migrated** to the database on first startup. After migration, changes to environment variables or properties are no longer taken into account — all configuration is now managed through the UI.

### Agents

#### Deploy agents

Once enabled, you should see Caldera available in your `Install agents` section:

OpenAEV has built-in instruction if you want command line examples to deploy the agent on one endpoint.

![Caldera deploy agents](../assets/caldera-deploy-agent.png)

!!! warning "Caldera AV detection"

    By default, the Caldera agent "Sandcat" is detected and blocked by antivirus. Here, we are using Caldera as a neutral executor that will execute implants that will execute threat arsenal actions, so you need to add the proper AV exclusions as instructed in the OpenAEV screen.

    ![Caldera AV exclusion](../assets/caldera-av.png)

#### Checks

All assets with a proper Caldera agent installed using the OpenAEV provided command line (then persistent) should now be
available in the OpenAEV endpoints list.

![Endpoints](../assets/caldera-endpoints.png)

#### Uninstallation

Run the following commands with an administrator Powershell in order to uninstall your Caldera agent:<br/>
`schtasks /delete /tn OpenAEVCaldera`<br/>
`Stop-Process -Name oaev-agent-caldera`<br/>
`rm -force -Recurse "C:\Program Files (x86)\Filigran\OAEV Caldera"`

## Implant Directories and Cleanup
For all executors (except Caldera and OpenAEV Agent) :

### Implant Directories
Implants are downloaded into a `runtimes/implant-XXXXX` subdirectory relative to the agent's installation directory, where `XXXXX` is a unique UUID generated for each inject execution.

Each implant directory is unique per inject.

The installation directory path varies depending on the agent type and installation mode. Refer to [installation mode](../../usage/openaev-agent.md#privileges-security_1) for the exact paths.

### Cleanup
Old implant directories are periodically cleaned up by the platform. At the interval defined by `clean-implant-interval` (default: **8 hours**), the platform sends a cleanup command to each endpoint agent to remove directories in `runtimes/` and `payloads/` that are older than **24 hours**.

!!! note "OpenAEV Agent"

    The OpenAEV Agent has its own built-in garbage collector with different thresholds. See the [OpenAEV Agent documentation](../../usage/openaev-agent.md) for details.
