<a href="https://filigran.io/products/openaev"><img src="./.github/img/readme/banner.svg" width="100%" alt="OpenAEV by Filigran"></a>

<p align="center">
  <a href="https://github.com/OpenAEV-Platform/openaev/stargazers"><img src="https://img.shields.io/github/stars/OpenAEV-Platform/openaev?style=flat-square&labelColor=0A0C1C&logo=github&logoColor=white&color=001BDA" alt="stars"></a>
  <a href="https://github.com/OpenAEV-Platform/openaev/releases/latest"><img src="https://img.shields.io/github/v/release/OpenAEV-Platform/openaev?style=flat-square&labelColor=0A0C1C&color=001BDA" alt="release"></a>
  <a href="https://hub.docker.com/r/openaev/platform"><img src="https://img.shields.io/docker/pulls/openaev/platform?style=flat-square&labelColor=0A0C1C&logo=docker&logoColor=white&color=001BDA" alt="docker pulls"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-001BDA?style=flat-square&labelColor=0A0C1C" alt="license"></a>
  <a href="https://github.com/OpenAEV-Platform/openaev/actions/workflows/core-ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/OpenAEV-Platform/openaev/core-ci.yml?branch=main&label=CI&style=flat-square&labelColor=0A0C1C&logo=githubactions&logoColor=white&color=001BDA" alt="CI"></a>
  <a href="https://docs.openaev.io"><img src="https://img.shields.io/badge/docs-read-001BDA?style=flat-square&labelColor=0A0C1C" alt="docs"></a>
  <a href="https://community.filigran.io"><img src="https://img.shields.io/badge/community-slack-001BDA?style=flat-square&labelColor=0A0C1C&logo=slack&logoColor=white" alt="community"></a>
</p>

> **Community workshop:** Join us as Taylor Walton (of SOCFortress) walks through his working SOC stack: OpenCTI → OpenAEV → Wazuh → Velociraptor. Taylor will be joined by OpenAEV's product owner to cover what's coming next for OpenCTI and OpenAEV.
>
> [Register](https://events.filigran.io/register/e751635a-9aec-49f5-801c-40cfded49c87?utm_medium=community&utm_source=github) · Dec 10 at 10 AM New York City time
>
> Join us on the
> [Filigran Community Slack](https://community.filigran.io/?utm_medium=community&utm_source=github).

## Introduction

OpenAEV is an open source platform allowing organizations to plan, schedule and conduct cyber adversary simulation
campaigns and tests.

![Screenshot](./.github/img/screenshot.png "Screenshot")

## Objective

The goal is to create a powerful, reliable and open source tool to effectively plan and play all types of simulations,
training and exercises from the technical level to the strategic one. Also, the platform is able to give you a proper
overview of any security gaps regarding actual threats with knowledge coming from
the [OpenCTI platform](https://opencti.io).

OpenAEV aims to respond to these issues, which not only concern state services but also many private organizations. With
different modules (scenarios, teams, simulations, verification of means of communication, encryption, etc.), the
platform offers advantages such as collaborative work, real-time monitoring, statistics or the management of feedback.

Finally, OpenAEV supports different types of inject, allowing the tool to be integrated with emails, SMS platforms,
social medias, alarm systems, etc. All currently supported integration can be found in
the [OpenAEV ecosystem](https://docs.openaev.io/latest/development/injectors/).

## Editions of the platform

OpenAEV platform has 2 different editions: Community (CE) and Enterprise (EE). The purpose of the Enterprise Edition is
to provide [additional and powerful features](https://filigran.io/offering/subscribe) which require specific investments
in research and development. You can enable the Enterprise Edition directly in the settings of the platform.

* OpenAEV Community Edition, licensed under the [Apache 2, Version 2.0 license](LICENSE).
* OpenAEV Enterprise Edition, licensed under the [Enterprise Edition license](LICENSE).

To understand what OpenAEV Enterprise Edition brings in terms of features, just check
the [Enterprise Editions page](https://filigran.io/offering/subscribe) on the Filigran website. You can also try this
edition by enabling it in the settings of the platform.

## Documentation and demonstration

If you want to know more on OpenAEV, you can read the [documentation on the tool](https://docs.openaev.io). If you wish
to discover how the OpenAEV platform is working, a [demonstration instance](https://demo.openaev.io) is available and
open to everyone. This instance is reset every night and is based on reference data maintained by the OpenAEV
developers.

## Releases download

The releases are available on the [Github releases page](https://github.com/OpenAEV-Platform/openaev/releases). You can
also access the [rolling release package](https://releases.openaev.io) generated from the main branch of the
repository.

## Installation

All you need to install the OpenAEV platform can be found in
the [official documentation](https://docs.openaev.io/latest/deployment/installation/). For installation, you can:

* [Use Docker](https://docs.openaev.io/latest/deployment/installation/#using-docker)
* [Install manually](https://docs.openaev.io/latest/deployment/installation/#manual-installation)

## Contributing

### Code of Conduct

OpenAEV has adopted a [Code of Conduct](CODE_OF_CONDUCT.md) that we expect project participants to adhere to. Please
read the [full text](CODE_OF_CONDUCT.md) so that you can understand what actions will and will not be tolerated.

### Contributing Guide

Read our [contributing guide](CONTRIBUTING.md) to learn about our development process, how to propose bugfixes and
improvements, and how to build and test your changes to OpenAEV.

### Beginner friendly issues

To help you get you familiar with our contribution process, we have a list
of [beginner friendly issues](https://github.com/OpenAEV-Platform/openaev/labels/good%20first%20issue) which are
fairly easy to implement. This is a great place to get started.

### Development

If you want to actively help OpenAEV, we created
a [dedicated documentation](https://docs.openaev.io/latest/development/environment_ubuntu/) about the
deployment of a development environment and how to start the source code modification.

## Community

Star OpenAEV to follow it and to help other security teams find it.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./.github/img/readme/stars-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="./.github/img/readme/stars-light.svg">
  <img alt="GitHub stars of OpenAEV over time" src="./.github/img/readme/stars-light.svg" width="100%">
</picture>

- **Questions and discussion**: the Filigran [community Slack](https://community.filigran.io).
- **Bugs and feature requests**: [GitHub issues](https://github.com/OpenAEV-Platform/openaev/issues).
- **Learn**: free courses on [Filigran Academy](https://academy.filigran.io).

## Contributing

Read the [contributing guide](CONTRIBUTING.md) to set up a development environment and propose a change. OpenAEV has adopted a [Code of Conduct](CODE_OF_CONDUCT.md). First contribution: the [good first issues](https://github.com/OpenAEV-Platform/openaev/labels/good%20first%20issue).

## Security

Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md), never in a public issue.

## License

OpenAEV is open source under the [Apache 2.0 license](LICENSE); the Enterprise Edition features are under the OpenAEV Enterprise Edition License, as stated in [LICENSE](LICENSE) and in each source file header.

## About Filigran

OpenAEV is designed and developed by [Filigran](https://filigran.io), the company behind the [XTM Platform](https://filigran.io) for Agentic CTEM.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./.github/img/readme/filigran-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="./.github/img/readme/filigran-light.svg">
  <img alt="Filigran" src="./.github/img/readme/filigran-light.svg" width="240">
</picture>
