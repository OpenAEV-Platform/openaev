# Breaking changes and migrations

This section lists breaking changes introduced in OpenAEV, per version starting with the latest.

Please follow the migration guides if you need to upgrade your platform.

## Breakdown per version

This table regroups all the breaking changes introduced, with the corresponding version in which the change was implemented.

| Change                                | Deprecated in | Changed in |
|:--------------------------------------|:--------------|:-----------|
| [OpenAEV encryption of secret](#openaev-encryption) | -         | 2.1.0      |
| [OpenAEV renaming](#openaev-renaming) | 1.18.20       | 2.0.0      |

## OpenAEV 2.1.0

### Introduction

<a id="openaev-encryption"></a>
#### OpenAEV encryption

With the introduction of the OpenAEV catalog, built-in connectors now store their configuration in the database. To ensure security, secrets and passwords within these configurations must be encrypted. This requires two new mandatory properties to be configured.

For more details, see [this migration guide](breaking-changes/2.1.0-encrypting-password.md)

## OpenAEV 2.0.0

### Deprecation

<a id="openaev-renaming"></a>
#### OpenAEV renaming

Following the evolution of scope in OpenBAS (Open Breach & Attack Simulation), it was decided to rename the project to OpenAEV (Open Adversarial Exposure Validation).

This platform allows you to entirely create custom attack scenarios to emulate on endpoints. You can even create your own automated tabletop crisis simulation.

All those changes require manual modifications to upgrade from previous versions of OpenBAS, even if a lot have been automated.

Take note that the first startup can be longer, all modifications have to be applied, and it can take a bit longer than usual.

For more details, see [this migration guide](breaking-changes/2.0.0-openaev-renaming.md)