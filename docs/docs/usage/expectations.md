# Expectations

Expectations define what is expected from an [Asset (endpoint)](assets.md) or
a [Players](teams_and_players_and_organizations.md#players-section) when facing an [Inject](injects.md) in terms of
security posture. Each expectation has a score representing how well it has been met by the target.

## Expectation types

Expectations can be categorized as either Manual or Automatic, depending on how they are validated.

### Manual expectations

Manual expectations require manual validation by the animation team, with the validation process and scoring managed
manually. They are simple, customizable expectations to be manually validated.

### Automatic expectations

Automatic expectations are validated automatically under specific conditions. Currently available automatic expectations
include:

- `Automatic - Prevention: Triggered when inject is processed`: automatically validated by security integration with
  compatible Collectors if the inject's action generates a prevention alert, such as quarantine.
- `Automatic - Detection: Triggered when inject is processed`: automatically validated by security integration with
  compatible Collectors if the inject's action generates a detection alert, such as an incident.
- `Automatic - Triggered when team reads articles`: Automatically validated when the article of a Media pressure inject
  has been read by targets.

For injects targeting asset groups, some expectations can be validated in two modes:

- All assets (per group) must validate the expectation.
- At least one asset (per group) must validate the expectation.

<!-- screenshot of an expectation form -->

!!! note "Special case: Publish Challenges inject "

    The "Publish Challenges" inject doesn't require an expectation, as results are computed directly from Challenges' scores.

## Expectation manipulation

### Add an expectation to an inject

To add expectations to an inject, navigate to the inject's content and click on "Add expectations". From there, select
the type of expectation you want to add and set a score for it.

You can add multiple expectations to a single inject.

<!-- screenshot -->

### Validate a manual expectation

If you have configured manual expectations in your scenario, you will have the opportunity to handle manual validations
during each simulation. During a Simulation, navigate to the Animation tab, under the Validation screen. Here, you'll
find a list of expectations that require manual validation.

<!-- screenshot of the screen populated with manual validation to perform -->

### Customize expectations

#### Default score

Expectations have a default score at creation. Depending on the expectation's type, there is a default
score value set in the system.

- In the Docker .env file thanks to these variables

| Parameter                                      | Environment variable                           | Default value | Description                                                         |
|:-----------------------------------------------|:-----------------------------------------------|:--------------|:--------------------------------------------------------------------|
| openbas.expectation.manual.default-score-value | OPENBAS_EXPECTATION_MANUAL_DEFAULT-SCORE-VALUE | 50            | Default score value for manual expectation                     |


#### Expiration time

Expectations results must be validated within a time limit. Depending on the expectation's type, there is a default
expiration time set in the system. You have two ways to modify that expiration time:

- In the Docker .env file thanks to these variables

| Parameter                                      | Environment variable                           | Default value | Description                                                         |
|:-----------------------------------------------|:-----------------------------------------------|:--------------|:--------------------------------------------------------------------|
| openbas.expectation.technical.expiration-time  | OPENBAS_EXPECTATION_TECHNICAL_EXPIRATION-TIME  | 21600         | Expiration time for Technical expectation (detection & prevention)  |
| openbas.expectation.detection.expiration-time  | OPENBAS_EXPECTATION_DETECTION_EXPIRATION-TIME  | 21600         | Expiration time for detection expectation                           |
| openbas.expectation.prevention.expiration-time | OPENBAS_EXPECTATION_PREVENTION_EXPIRATION-TIME | 21600         | Expiration time for prevention expectation                          |
| openbas.expectation.human.expiration-time      | OPENBAS_EXPECTATION_HUMAN_EXPIRATION-TIME      | 86400         | Expiration time for human expectation (manual, challenge & article) |
| openbas.expectation.challenge.expiration-time  | OPENBAS_EXPECTATION_CHALLENGE_EXPIRATION-TIME  | 86400         | Expiration time for challenge expectation                           |
| openbas.expectation.article.expiration-time    | OPENBAS_EXPECTATION_ARTICLE_EXPIRATION-TIME    | 86400         | Expiration time for article expectation                             |
| openbas.expectation.manual.expiration-time     | OPENBAS_EXPECTATION_MANUAL_EXPIRATION-TIME     | 86400         | Expiration time for manual expectation                              |

A default expiration time is set for technical and human expectations. Users can override them for each type of
expectations.

- In the UI

![Customize expiration time in UI](assets/expectation_customize_expiration_time.png)

When creating an expectation, users can set an expiration time. The system's default times are set on the form and users
decide to override it.
