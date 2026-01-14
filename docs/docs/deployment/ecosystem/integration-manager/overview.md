# Integration Manager

## Introduction

The OpenAEV Integration Manager is a deployment tool that simplifies the management and deployment of **external** collectors, injectors and executors within the platform.

!!! info 

    - **Built-in** collectors, injectors and executors are natively integrated and do not require the Integration Manager.

## Architecture
To learn more about Xtm Composer architecture, refer to the [dedicated documentation](https://docs.opencti.io/latest/deployment/integration-manager/architecture/).

## How to use in OpenAEV

### Prerequisites

- An **Enterprise Edition license** is required to use this feature. Without it, the catalog is available in read-only mode. You can enter the license key from:
    - the **Deploy** action on a collector, injector, or executor card,
    - the collector, injector, or executor's detail view,
    - or the **Settings** page.

![EE required](../../assets/integration-manager/ee-required.png)

- The **Integration Manager** must be installed and operational to deploy collectors, injectors or executors.

![composer required](../../assets/integration-manager/composer-required.png)

## Browsing the catalog
- Navigate to **Integrations > Catalog**
- Use the search bar to find collectors, injectors and executors by name or description. You can also apply filters (e.g., by collector, executor or injector type).
- If a collector, injector or executor has already been deployed, a **badge** will appear on its **Deploy** button.

## Deploying a collector, injector or executor
1. Click the **Deploy** button on an external collector, injector or executor card or from the detail view. A form will appear with required configuration fields.
2. Fill in the required options (you can also expand **Advanced options** to configure additional settings)
   ![instance form](../../assets/integration-manager/instance-form-sample.png)

    !!! note "Configuration information"
      
        - **Instance names**: Two instances can't share the same name
        - **Validation error**: If a duplicate name is detected, a blocking error will prevent deployment until a unique name is provided
        - **Log level**: set the desired confidence log level for the service account.
        - **Additional options**: collector, injector or executor specific configuration.

3. Click **Create**. Once the collector is created, you will be redirected to the collector instance view.

    !!! note "Instance created"
    
        Newly created external collectors, injectors or executors are not started automatically. 
        You can still update their configuration via the **Update** action.

5. When ready, click **Start** to run it.
6. From the instance view, you can also check the **Logs** tab. The displayed logs depend on the logging level configured.


## Managing the instances

- Different injector, collector or executor types are identified in the catalog:   
     - External : Injector, collector or executor managed by the integration manager  
     - built-in : Injector, collector or executor natively integrated into the core platform no additional deployment required
- Instances statuses:
      - Managed instances: *Started* or *Stopped*.
- Only **managed instances** can be started/stopped from the UI. They are also the only ones that provide logs in the interface.
- Updating the configuration of a managed instance is possible. Changes will take effect after a short delay.
- For security reasons, API keys/tokens are encrypted when saved and never displayed in the UI afterward.
