## Expectations — Scoring, Expiration & UI

This page explains how to **configure, validate and operate expectations**.

## Add an Expectation to an Inject

1. Open the Inject
2. Click **Add expectations**
3. Select the expectation type
4. Configure: score, validation mode, expiration time (optional)

💡 You can attach **multiple expectations** to a single Inject.

![Create manual expectation](assets/create_manual_expectation.png)

## Validate Expectations

### Manual expectations

* During a simulation: **Animation → Validation**
* During atomic testing: **Overview** tab

![Validate a manual expectation from the animation tab](assets/manual_expectation_validation_animation_tab.png)

### Technical expectations

* Automatically validated via collectors
* Or validated manually by adding detection or prevention results

![Add the validation of a technical expectation in atomic testing](assets/add_technical_expectation_validation.png)

## Scoring

### Default score

Each expectation type has a **default score**, applied at creation.

| Type   | Default score |
| ------ | ------------- |
| Manual | 50            |

Configured via environment variables.

| Parameter                                      | Environment variable                           | Default value | Description                                |
|:-----------------------------------------------|:-----------------------------------------------|:--------------|:-------------------------------------------|
| openaev.expectation.manual.default-score-value | OPENAEV_EXPECTATION_MANUAL_DEFAULT-SCORE-VALUE | 50            | Default score value for manual expectation |

## Expiration Time

Expectations must validate **within a defined time window**.

If the time expires:

* the expectation **fails automatically**
* the result is marked as:

    * `Not Detected`
    * `Not Prevented`
    * or equivalent

### Default expiration values

| Expectation type                   | Default  |
| ---------------------------------- | -------- |
| Detection / Prevention             | 6 hours  |
| Human (manual, article, challenge) | 24 hours |

You can override expiration times:

* globally (environment variables)
* per expectation (UI)

| Parameter                                      | Environment variable                           | Default value | Description                                                         |
|:-----------------------------------------------|:-----------------------------------------------|:--------------|:--------------------------------------------------------------------|
| openaev.expectation.technical.expiration-time  | OPENAEV_EXPECTATION_TECHNICAL_EXPIRATION-TIME  | 21600         | Expiration time for Technical expectation (detection & prevention)  |
| openaev.expectation.detection.expiration-time  | OPENAEV_EXPECTATION_DETECTION_EXPIRATION-TIME  | 21600         | Expiration time for detection expectation                           |
| openaev.expectation.prevention.expiration-time | OPENAEV_EXPECTATION_PREVENTION_EXPIRATION-TIME | 21600         | Expiration time for prevention expectation                          |
| openaev.expectation.human.expiration-time      | OPENAEV_EXPECTATION_HUMAN_EXPIRATION-TIME      | 86400         | Expiration time for human expectation (manual, challenge & article) |
| openaev.expectation.challenge.expiration-time  | OPENAEV_EXPECTATION_CHALLENGE_EXPIRATION-TIME  | 86400         | Expiration time for challenge expectation                           |
| openaev.expectation.article.expiration-time    | OPENAEV_EXPECTATION_ARTICLE_EXPIRATION-TIME    | 86400         | Expiration time for article expectation                             |
| openaev.expectation.manual.expiration-time     | OPENAEV_EXPECTATION_MANUAL_EXPIRATION-TIME     | 86400         | Expiration time for manual expectation                              |
