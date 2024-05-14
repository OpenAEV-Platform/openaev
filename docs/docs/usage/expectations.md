# Expectations

Expectations define what is expected from an [Asset (endpoint)](assets.md) or a [Players](teams_and_players_and_organizations.md#players-section) when facing an [Inject](injects.md) in terms of security posture. Each expectation has a score representing how well it has been met by the target.


## Expectation types

Expectations can be categorized as either Manual or Automatic, depending on how they are validated.

### Manual expectations

Manual expectations require manual validation by the animation team, with the validation process and scoring managed manually. They are simple, customizable expectations to be manually validated.


### Automatic expectations

Automatic expectations are validated automatically under specific conditions. Currently available automatic expectations include:

- `Automatic - Prevention: Triggered when inject is processed`: automatically validated by security integration with compatible Collectors if the inject's action generates a prevention alert, such as quarantine.
- `Automatic - Detection: Triggered when inject is processed`: automatically validated by security integration with compatible Collectors if the inject's action generates a detection alert, such as an incident.
- `Automatic - Triggered when team reads articles`: Automatically validated when the article of a Media pressure inject has been read by targets.

For injects targeting asset groups, some expectations can be validated in two modes:

- All assets (per group) must validate the expectation.
- At least one asset (per group) must validate the expectation.

<!-- screenshot of an expectation form -->

!!! note "Special case: Publish Challenges inject "

    The "Publish Challenges" inject doesn't require an expectation, as results are computed directly from Challenges' scores.


## Expectation manipulation

### Add an expectation to an inject

To add expectations to an inject, navigate to the inject's content and click on "Add expectations". From there, select the type of expectation you want to add and set a score for it.

You can add multiple expectations to a single inject.

<!-- screenshot -->


### Validate a manual expectation

If you have configured manual expectations in your scenario, you will have the opportunity to handle manual validations during each simulation. During a Simulation, navigate to the Animation tab, under the Validation screen. Here, you'll find a list of expectations that require manual validation.

<!-- screenshot of the screen populated with manual validation to perform -->

