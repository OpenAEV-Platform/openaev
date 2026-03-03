# Breaking changes and migrations

This section lists breaking changes introduced in OpenAEV, per version starting with the latest.

Please follow the migration guides if you need to upgrade your platform.

## Breakdown per version

This table regroups all the breaking changes introduced, with the corresponding version in which the change was
implemented.

| Change                                                      | Deprecated in | Changed in |
|:------------------------------------------------------------|:--------------|:-----------|
| [OpenCTI / OpenAEV compatibility](#octi-oaev-compatibility) | -             | 2.2.0      |
| [OpenAEV encryption of secret](#openaev-encryption)         | -             | 2.1.0      |
| [OpenAEV renaming](#openaev-renaming)                       | 1.18.20       | 2.0.0      |

## OpenAEV 2.2.0

### Introduction

<a id="octi-oaev-compatibility"></a>

#### Scenario Generation from OpenCTI Security Coverage

In **OpenAEV 2.2.0**, the interconnection between OpenCTI and OpenAEV requires matching major versions:

- **OpenAEV 2.2.0** only works with **OpenCTI V7**
- **OpenCTI V7** only works with **OpenAEV 2.2.0**

Due to API and interconnection changes introduced in OpenCTI V7, previous versions of OpenCTI are not compatible
with OpenAEV 2.2.0, and conversely, OpenCTI V7 is not compatible with earlier versions of OpenAEV.

!!! success "Resolved in OpenAEV 2.2.1"

    Backwards compatibility with older OpenCTI versions has been restored starting from **OpenAEV 2.2.1**. This breaking change only affects **OpenAEV 2.2.0**.

If you are upgrading to OpenAEV 2.2.0, please make sure to upgrade both OpenCTI and OpenAEV simultaneously to avoid service disruption.

For more details, see [this migration guide](breaking-changes/2.2.0-opencti-security-coverage.md)

## OpenAEV 2.1.0

### Introduction

<a id="openaev-encryption"></a>
#### OpenAEV encryption

With the introduction of the OpenAEV catalog, built-in connectors now store their configuration in the database. To
ensure security, secrets and passwords within these configurations must be encrypted. This requires two new mandatory
properties to be configured.

For more details, see [this migration guide](breaking-changes/2.1.0-encrypting-password.md)

## OpenAEV 2.0.0

### Deprecation

<a id="openaev-renaming"></a>

#### OpenAEV renaming

Following the evolution of scope in OpenBAS (Open Breach & Attack Simulation), it was decided to rename the project to
OpenAEV (Open Adversarial Exposure Validation).

This platform allows you to entirely create custom attack scenarios to emulate on endpoints. You can even create your
own automated tabletop crisis simulation.

All those changes require manual modifications to upgrade from previous versions of OpenBAS, even if a lot have been
automated.

Take note that the first startup can be longer, all modifications have to be applied, and it can take a bit longer than
usual.

For more details, see [this migration guide](breaking-changes/2.0.0-openaev-renaming.md)