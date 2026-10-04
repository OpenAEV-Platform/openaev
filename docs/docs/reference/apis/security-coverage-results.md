# Security Coverage result bundle

When a Simulation of a Scenario generated from an OpenCTI Security Coverage ends, OpenAEV sends its results back to
OpenCTI as a **STIX (Structured Threat Information Expression) 2.1 bundle**. This page documents the content of that
bundle: the objects it contains, their properties and how every score is computed.

OpenCTI uses this bundle to display the Security Coverage results and to feed the validation layer of its
threat-informed defense views, where each result is attributed to the security platform that detected or prevented the
attack.

!!! note "Where the bundle comes from"

    OpenAEV builds the bundle once the Simulation is finished and pushes it to the OpenCTI instance connected to the
    Simulation's Tenant. See [Security Coverage enrichment (XTM Suite)](../../usage/evaluate/xtm-suite-connector.md)
    to connect OpenCTI.

## Bundle content

| Object                          | STIX type                                     | Count                                       | Content                                                                               |
|:--------------------------------|:----------------------------------------------|:--------------------------------------------|:--------------------------------------------------------------------------------------|
| Security Coverage               | `security-coverage`                           | 1                                           | The Security Coverage received from OpenCTI, completed with the overall results       |
| Covered object relationship     | `relationship` (`has-covered`)                | 1 per covered object                        | Results of the Injects matching one attack pattern, vulnerability, indicator or artifact |
| Security platform               | `identity` (`identity_class: securityplatform`) | 1 per security platform name with results  | The security platform, with a STIX id derived from its name (see below)               |
| Security platform relationship  | `relationship` (`has-covered`)                | 1 per security platform identity            | Overall results of one security platform identity across the Simulation               |

Every relationship has the Security Coverage as `source_ref`.

### Security platform identity id

The STIX id of a security platform identity is not its OpenAEV id. It is derived from the platform name, exactly like
the standard id OpenCTI gives an identity, so the same platform resolves to the same OpenCTI identity across Simulations
and OpenAEV instances:

1. Lowercase the platform name, then trim it the way JavaScript `String.prototype.trim` does: every Unicode space
   (no-break and em spaces included), tab, line terminator and byte order mark at either end is removed.
2. Serialize `{"identity_class":"securityplatform","name":"<normalized name>"}` as JSON with sorted keys and no spaces.
3. Compute the UUID version 5 of this JSON in the namespace `00abedb4-aa42-466c-9c01-fed23315a9b7`.

For example, `CrowdStrike Falcon` is `identity--fd6bb94b-46b7-5e41-90d0-2a0fcf171ca2`. Security platforms sharing a
name (with different types) are one identity: the bundle emits it once, and its scores and relationship cover the
results of all of them, with the best verdict of the platforms on each Expectation (the worst one for a vulnerability,
as in `coverage`).

## Scores

Every score of the bundle is a success rate in percentage points, from `0` to `100`, rounded to the nearest point. It is
computed per expectation type, from the primary Expectations of the Injects, which are all the Expectations attached to
neither an agent nor a player:

- team Expectations (manual, article and challenge Expectations of a team, `HUMAN_RESPONSE`);
- asset Expectations and asset group Expectations;
- Expectations attached to no team, asset or asset group;
- the Expectation of one asset of a targeted asset group only when that asset is also a direct target of the Inject.

The other rows are rolled up before the count and never counted twice: agent Expectations into their asset Expectation,
player Expectations into their team Expectation, and the asset Expectations of a targeted asset group into the group
Expectation, according to its validation mode (all assets or at least one asset).

The scores of the covered objects (`coverage` and `coverage_platforms`) and of the security platform relationships start
from the same primary Expectations. An Inject without any primary Expectation, for example one with agent Expectations
only, is scored there on all its Expectations, so the results its agents received are still counted and attributed, and
the `covered` and `coverage` of a covered object always agree with its `coverage_platforms`. Only the `coverage` of the
Security Coverage object, the overall score of the Simulation, counts primary Expectations only. When a platform
reported both on an asset Expectation and on its agents, a detection or prevention success on the asset wins and its
agents decide otherwise; for a vulnerability the worst verdict wins, so a vulnerable result on the asset or on any of
its agents makes the asset vulnerable for that platform.

| Expectation outcome                                                           | Counted as |
|:------------------------------------------------------------------------------|:-----------|
| Score greater than or equal to the expected score                             | Success    |
| Team Expectation (article, challenge or manual answered by a team) below the expected score | Failure    |
| Other Expectation with a score of `0`                                         | Failure    |
| Other Expectation with any other score below the expected score               | Partial    |
| No score yet                                                                  | Pending    |

A team Expectation is either reached or failed: a non-zero score below the expected score still counts as a failure,
so `HUMAN_RESPONSE` scores never include partial answers of teams.

The score is the number of successes divided by the number of counted Expectations, so partial, failed and pending
Expectations all lower it.

Scores are listed as `{ "name": <expectation type>, "score": <percentage points> }` entries. The expectation types are:

| Name             | Expectations                    |
|:-----------------|:--------------------------------|
| `PREVENTION`     | Prevention                      |
| `DETECTION`      | Detection                       |
| `VULNERABILITY`  | Vulnerability                   |
| `HUMAN_RESPONSE` | Manual, article and challenge   |

A type is listed only when at least one counted Expectation has that type.

## Security Coverage object

| Property                  | Description                                                                                      |
|:--------------------------|:-------------------------------------------------------------------------------------------------|
| `coverage`                | Scores of every Inject of the Simulation                                                         |
| `external_uri`            | Link to the Scenario in OpenAEV, or to the Simulation when it has no Scenario                    |
| `valid_from`, `last_result` | Start of the Simulation                                                                        |
| `valid_to`                | Start of the next Simulation of the Scenario: the following Simulation, or the next scheduled one |
| `auto_enrichment_disable` | Always `false`                                                                                   |
| `tenant_id`, `tenant_name` | Tenant of the Simulation, when the preview feature `TENANT_FIELDS_FOR_SECURITY_COVERAGE` is enabled |

The other properties are the ones received from OpenCTI.

## Covered object relationships

One `has-covered` relationship targets each object of the Security Coverage: attack patterns, indicators, artifacts,
and vulnerabilities when the preview feature `STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES` is enabled.

The results of a covered object are computed only from the Injects matching it:

| Covered object  | Matching Injects                                                                          |
|:----------------|:------------------------------------------------------------------------------------------|
| Attack pattern  | Injects whose Action references the attack pattern (matched by external ID)              |
| Vulnerability   | Injects whose Action references the vulnerability (matched by external ID)               |
| Indicator       | The Inject resolving the DNS hostname of the indicator                                    |
| Artifact        | The Inject whose file drop Payload drops the artifact                                     |

| Property             | Description                                                                                           |
|:---------------------|:------------------------------------------------------------------------------------------------------|
| `target_ref`         | STIX id of the covered object                                                                         |
| `covered`            | `true` when at least one matching Inject defines an Expectation                                       |
| `coverage`           | Scores of the matching Injects, all sources together. Only present when `covered` is `true`          |
| `coverage_platforms` | Scores of the matching Injects attributed to each security platform, see below. Omitted when no security platform has a result on the matching Injects |
| `start_time`         | Start of the Simulation                                                                               |
| `stop_time`          | Start of the next Simulation of the Scenario                                                         |
| `external_uri`       | Link to the Scenario in OpenAEV, when the preview feature `TENANT_FIELDS_FOR_SECURITY_COVERAGE` is enabled |

### Per-platform attribution (`coverage_platforms`)

`coverage_platforms` tells which security platform detected or prevented the covered object. It lists one entry per
security platform and expectation type:

| Field          | Description                                                                                         |
|:---------------|:----------------------------------------------------------------------------------------------------|
| `platform_ref` | STIX id of the security platform identity, always present in the same bundle                       |
| `name`         | Expectation type, named like in `coverage`                                                          |
| `score`        | Success rate in percentage points, rounded like in `coverage`                                       |

A result belongs to a security platform when the platform is the source asset of the result. This covers the results of
a Collector linked to the platform (for example an EDR (Endpoint Detection and Response) or SIEM (Security Information
and Event Management) Collector), the results written by the platform itself, and the manual validations of a detection
or prevention against the platform.

Each security platform is scored only on the Expectations of the matching Injects it reported on:

- An Expectation without any result of the platform is out of its scope (another platform monitors that asset, or the
  platform does not handle that expectation type) and does not lower its score.
- A platform expected to answer that never did keeps a pending or expired result, which counts against its score.
- An expectation type is listed for a platform only when the platform reported on at least one Expectation of that type,
  so a SIEM that only detects is never listed with a `PREVENTION` score.
- When a platform is the only one reporting and it reports on every Expectation, its score equals the `coverage`
  score. With several reporting platforms, the score of an Expectation in `coverage` combines all its sources. For
  detection and prevention the best result wins, so another platform's better result can make `coverage` higher than
  the score of each platform. For vulnerability the worst verdict wins, so a vulnerable result of one platform makes
  the Expectation vulnerable in `coverage` even when another platform reported it not vulnerable.

Only the security platforms of the Simulation's Tenant are attributed.

!!! example "Attack pattern covered by two Injects"

    Technique `T1059.001` is covered by two Injects. On the first one, the EDR of the workstations detects and prevents
    the attack. On the second one, the NDR (Network Detection and Response) watching the servers misses it, and nothing
    prevents it. The relationship of the attack pattern carries:

    ```json
    {
      "type": "relationship",
      "id": "relationship--5b6c1a8e-2f6d-3c1b-9d4e-7a0f2b8c9d10",
      "relationship_type": "has-covered",
      "source_ref": "security-coverage--0f6f1d9e-3a52-4c2e-8b1a-6a2f5d7c9e01",
      "target_ref": "attack-pattern--dcaa092b-7de9-4a21-977f-7fcb77e89c48",
      "covered": true,
      "coverage": [
        { "name": "PREVENTION", "score": 50 },
        { "name": "DETECTION", "score": 50 }
      ],
      "coverage_platforms": [
        { "platform_ref": "identity--fd6bb94b-46b7-5e41-90d0-2a0fcf171ca2", "name": "PREVENTION", "score": 100 },
        { "platform_ref": "identity--fd6bb94b-46b7-5e41-90d0-2a0fcf171ca2", "name": "DETECTION", "score": 100 },
        { "platform_ref": "identity--2d8bfd8f-30fd-56aa-b89d-742301ab549f", "name": "DETECTION", "score": 0 }
      ],
      "start_time": "2026-09-23T14:09:43Z",
      "stop_time": "2026-09-24T14:09:43Z"
    }
    ```

    Both `platform_ref` values are `identity` objects of the same bundle: `CrowdStrike Falcon` (the EDR) and `Vectra AI`
    (the NDR), whose ids derive from their names (UUIDv5, see above).

## Security platform relationships

One `has-covered` relationship targets each security platform identity of the bundle.

| Property     | Description                                                                                                  |
|:-------------|:-------------------------------------------------------------------------------------------------------------|
| `target_ref` | STIX id of the security platform identity                                                                   |
| `covered`    | `true` when at least one Inject of the Simulation defines an Expectation                                    |
| `coverage`   | Scores of the counted Expectations of the Simulation (see [Scores](#scores): the primary Expectations, plus all the Expectations of an Inject without any primary one), each one scored with the results of this platform only: a counted Expectation the platform did not report on counts as pending |
| `start_time`, `stop_time`, `external_uri` | Same as on the covered object relationships                                    |

## Compatibility

`coverage_platforms` is an additional property: `covered`, `coverage` and every other property keep their meaning, so
OpenCTI versions that do not know `coverage_platforms` ignore it. OpenCTI versions that support it store it on the
relationship as `coverage_platforms_information`.

## What's next?

- [Security Coverage enrichment (XTM Suite)](../../usage/evaluate/xtm-suite-connector.md) -- Connect OpenCTI and send the results automatically
- [Scenario generation from OpenCTI Security Coverage](../../usage/build/scenario/security-coverage.md) -- How OpenAEV builds the Scenario from the Security Coverage
- [Expectations](../../usage/evaluate/expectations/expectations.md) -- How Expectations are scored
