!!! tip "Filigran"

    [Filigran](https://filigran.io) is providing an [Enterprise Edition](https://filigran.io/offerings/openaev-enterprise-edition) of the platform, whether [on-premise](https://filigran.io/offerings/professional-support-packages) or in the [SaaS](https://filigran.io/offerings/software-as-a-service).

## What is OpenAEV EE?

OpenAEV Enterprise Edition (EE) is based on the open core concept. This means that the source code of OpenAEV EE remains open
source and included in the main GitHub repository of the platform but is published under a specific license. As
specified in the GitHub license file:

- The OpenAEV Community Edition is licensed under the Apache License, Version 2.0 (the "Apache License").
- The OpenAEV Enterprise Edition is licensed under the OpenAEV Enterprise Edition License (the "Enterprise Edition
  License").

The source files in this repository have a header indicating which license they are under. If no such header is
provided, this means that the file belongs to the Community Edition under the Apache License, Version 2.0.

## EE activation

Enterprise Edition is easy to activate. You need to go the platform settings and click on the "Manage your Enterprise
Edition License" button.

![OpenAEV activation](assets/enterprise-activate.png)

Then you will need to put a valid OpenAEV EE license. If you don't have it, you
can [generate a trial license](https://filigran.io/enterprise-editions-trial/).

![OpenAEV EE EULA](assets/enterprise-license-agreement.png)

As a reminder:

- Filigran is the only company producing and providing OpenAEV Enterprise Edition license keys.
- Filigran can provide free-to-use OpenAEV Enterprise Edition licenses for development and research purposes (e.g. connector development, integrations with technical partners, etc.) as well as for non-governmental charity organizations.
- OpenAEV Enterprise Edition licenses are automatically provided to all Filigran SaaS customers.
- **For all other usages including on-premise deployments, OpenAEV Enterprise Edition is reserved to organizations that have signed a Filigran Enterprise agreement.**

## Available features

### Generative AI

Be able to use AI for content generation including emails, media pressure articles etc.

### CrowdStrike Falcon agent

The CrowdStrike Falcon agent can be leveraged to execute implants as detached processes that will then execute threat arsenal actions
according to the [OpenAEV architecture](../deployment/platform/overview.md#architecture).

### Tanium agent

The Tanium agent can be leveraged to execute implants as detached processes that will then execute threat arsenal actions
according to the [OpenAEV architecture](../deployment/platform/overview.md#architecture).

### SentinelOne agent

The SentinelOne agent can be leveraged to execute implants as detached processes that will then execute threat arsenal actions
according to the [OpenAEV architecture](../deployment/platform/overview.md#architecture).

### Palo Alto Cortex agent

The Palo Alto Cortex agent can be leveraged to execute implants as detached processes that will then execute threat arsenal actions
according to the [OpenAEV architecture](../deployment/platform/overview.md#architecture).

On Windows, because Palo Alto Cortex whitelists its own process tree, OpenAEV creates a scheduled task to detach the process that will execute the threat arsenal actions.

### Microsoft Defender for Endpoint (MDE) agent

Microsoft Defender for Endpoint can be leveraged to execute implants as detached processes that will then execute threat arsenal actions
according to the [OpenAEV architecture](../deployment/platform/overview.md#architecture).

OpenAEV reuses the MDE sensor already deployed on your endpoints and drives it through the Live Response API. On Windows, the implant is launched from a self-deleting SYSTEM scheduled task so it survives the Live Response session teardown. See the [MDE Executor deployment guide](../deployment/ecosystem/executors.md#mde-agent) for the required Azure app permissions and Live Response setup.

## Remediations in CVEs

More detail: [CVEs](taxonomies.md) and [Findings view](../usage/evaluate/findings/findings.md).

## Detection remediation in threat arsenal actions and Injects

More detail: [Detection remediations in threat arsenal actions](../usage/build/threat-arsenals/threat-arsenals.md)
and [Atomic testing remediations](../usage/evaluate/atomic-testing/atomic-testing.md).

## More to come

More features will be available in OpenAEV in the future. Features like:

- Security posture automatic evaluation.
- Premium mitigations and recommendation for configuration changes.
