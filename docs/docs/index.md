---
hide:
  - navigation
  - toc
---

# OpenAEV Documentation Space

Welcome to the OpenAEV documentation: how to deploy, use and administer the platform.

!!! info "Release notes"

    Please, be sure to also take a look at the [OpenAEV releases notes](https://github.com/OpenAEV-Platform/openaev/releases), they may contain important information about releases and deployments.

## Introduction

OpenAEV is an open source Adversarial Exposure Validation platform. It lets you test your defenses against real attack techniques and measure how they respond:

- run Simulations and Atomic Tests built from Threat Arsenal Actions mapped to MITRE ATT&CK, on your endpoints, cloud and AI targets;
- chain Actions into attack paths, or let an AI orchestrator plan and run them with Autonomous Attack Chaining;
- check what your security tools (EDR, SIEM) detected and prevented, through Collectors;
- generate Scenarios from the threats tracked in OpenCTI.

!!! tip "Docker deployment"

    To deploy OpenAEV alone with Docker, use the [OpenAEV Docker repository](https://github.com/OpenAEV-Platform/docker). To deploy the full eXtended Threat Management (XTM) suite (OpenCTI, OpenAEV and XTM One), use the [XTM Docker repository](https://github.com/FiligranHQ/xtm-docker).

## Getting started

<div class="grid cards" markdown>

-   :material-rocket-launch-outline:{ .lg .middle } __Deploy__

    ---

    Install and configure the platform, the Integration Manager, and follow the breaking changes between versions.

    [:octicons-arrow-right-24:{ .middle } Deploy now](deployment/platform/overview.md)

-   :material-puzzle-outline:{ .lg .middle } __Integrations__

    ---

    Install the OpenAEV agent, and deploy the Executors, Injectors and Collectors that connect OpenAEV to your tools.

    [:octicons-arrow-right-24:{ .middle } Connect](integrations/agents/openaev-agent.md)

-   :fontawesome-regular-compass:{ .lg .middle } __User guide__

    ---

    Build Scenarios, run Simulations and Atomic Tests, chain attacks, and read the results.

    [:octicons-arrow-right-24:{ .middle } Explore](usage/get-started/getting-started.md)

-   :material-tune-vertical:{ .lg .middle } __Administration__

    ---

    Manage users, groups and roles, Tenants, platform settings and taxonomies.

    [:octicons-arrow-right-24:{ .middle } Customize](administration/introduction.md)

-   :material-book-open-variant:{ .lg .middle } __Reference__

    ---

    Look up configuration parameters, the REST API and its filters.

    [:octicons-arrow-right-24:{ .middle } Look up](reference/deployment/configuration.md)

-   :material-lifebuoy:{ .lg .middle } __Troubleshooting & FAQ__

    ---

    Find what to check when something does not work as expected.

    [:octicons-arrow-right-24:{ .middle } Troubleshoot](troubleshooting/index.md)

</div>

!!! info "Need more help?"

    We are doing our best to keep this documentation complete, accurate and up to date. 
    
    If you still have questions or you find something which is not sufficiently explained, join the [Filigran Community on Slack](https://community.filigran.io).


## Blog posts

<div class="grid cards" markdown>

-   :material-newspaper-variant-outline:{ .lg .middle } __Resources and content__

    ---

    Discover tutorials, best practices and deep dives on OpenAEV features on our Filigran blog.

    [:octicons-arrow-right-24:{ .middle } Read now](https://blog.filigran.io)
</div>

## Additional resources

Below, you will find external resources which may be useful along your OpenAEV journey.

<div class="grid" markdown>

[**:material-school-outline:{ .middle } Training Courses**](https://academy.filigran.io)<br />
Training courses for analysts and administrators in the Filigran Academy.

[**:material-youtube:{ .middle } Video materials**](https://www.youtube.com/@Filigran/videos)<br />
Set of video illustrating the implementation of use cases and platform capabilities.

</div>
