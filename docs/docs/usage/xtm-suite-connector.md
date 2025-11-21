# XTM Suite: automated enrichment of Security Coverage

!!! tip "Under construction"

    We are doing our best to complete this page. If you want to participate, don't hesitate to join the [Filigran Community on Slack](https://community.filigran.io) or submit your pull request on the [Github doc repository](https://github.com/OpenAEV-Platform/docs).


OpenAEV enables other products from the XTM Suite to benefit from a comprehensive Security Coverage enrichment for a given Adversarial Exposure scenario.
This means that OpenAEV can be triggered via an XTM Suite product to execute a scenario based on a desired scenario, and results from the scenario execution such as Detection rate, Prevention rate... can
be returned to the triggering product for ingestion.

This feature is currently available for the following product:

* OpenCTI

## Automated enrichment for OpenCTI

### Ensuring an up and running OpenCTI instance
This feature requires an active OpenCTI instance. Refer to the [OpenCTI documentation](https://docs.opencti.io/latest/) for enabling this instance.

Once the OpenCTI instance is up and running, make sure to obtain these two settings:

* The instance's full domain name (i.e. _https://opencti.domain.example_)
* A valid API Token associated with an account with sufficient privileges (refer to: [Configuring the Connector API token](https://docs.opencti.io/latest/deployment/connectors/#connector-token))

### Enabling the Security Coverage connector in OpenAEV
Make sure you set a value for all mandatory configuration keys, following the [Configuration documentation for the Security Coverage Connector](../../deployment/configuration#xtm-suite-opencti).

### Use OpenCTI to trigger security coverage enrichments seamlessly
The connector is now up and running and should be visible in OpenCTI as _OpenAEV Coverage_.
![Active OpenAEV Coverage connector in OpenCTI](assets/active_openaev_connector_in_opencti.png)
Refer to the [OpenCTI documentation](https://docs.opencti.io/latest/) for how to trigger the enabled connector to get automated enriched security posture assessments with OpenAEV.