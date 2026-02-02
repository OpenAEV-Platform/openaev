<h1 align="center">
  <a href="https://openaev.io"><img src="./.github/img/logo_openaev.png" alt="OpenAEV"></a>
</h1>
<p align="center">
  <a href="https://openaev.io" alt="Website"><img src="https://img.shields.io/badge/website-openaev.io-blue.svg" /></a>
  <a href="https://docs.openaev.io" alt="Documentation"><img src="https://img.shields.io/badge/documentation-latest-orange.svg" /></a>
  <a href="https://community.filigran.io" alt="Slack"><img src="https://img.shields.io/badge/slack-3K%2B%20members-4A154B" /></a>
  <a href="https://drone.filigran.io/OpenAEV-Platform/openaev"><img src="https://drone.filigran.io/api/badges/OpenAEV-Platform/openaev/status.svg" /></a>
  <a href="https://codecov.io/gh/OpenAEV-Platform/openaev"><img src="https://codecov.io/gh/OpenAEV-Platform/openaev/graph/badge.svg" /></a>
  <a href="https://deepscan.io/dashboard#view=project&tid=11710&pid=14631&bid=276803"><img src="https://deepscan.io/api/teams/11710/projects/14631/branches/276803/badge/grade.svg" alt="DeepScan grade"></a>
  <a href="https://hub.docker.com/u/openaev" alt="Docker pulls"><img src="https://img.shields.io/docker/pulls/openaev/platform" /></a>
</p>

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
also access the [rolling release package](https://releases.openaev.io) generated from the master branch of the
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

### Status & bugs

Currently OpenAEV is under heavy development, if you wish to report bugs or ask for new features, you can directly use
the [Github issues module](https://github.com/OpenAEV-Platform/openaev/issues).

### Discussion

If you need support or you wish to engage a discussion about the OpenAEV platform, feel free to join us on
our [Slack channel](https://community.filigran.io). You can also send us an email to contact@filigran.io.

## About

### Authors

OpenAEV is a product designed and developed by the company [Filigran](https://filigran.io).

<a href="https://filigran.io" alt="Filigran"><img src="./.github/img/logo_filigran.png" width="300" /></a>

### Data Collection

#### Usage telemetry

To improve the features and the performances of OpenAEV, the platform collects anonymous statistical data related to its
usage and health.

You can find all the details on collected data and associated usage in
the [usage telemetry documentation](https://docs.openaev.io/latest/reference/deployment/telemetry/).