# Expectations

!!! tip "Under construction"

    We are doing our best to complete this page. If you want to participate, don't hesitate to join the [Filigran Community on Slack](https://community.filigran.io) or submit your pull request on the [Github doc repository](https://github.com/OpenBAS-Platform/docs).

Expectations are the mean to express what is expected from an Asset (endpoint) or a Players facing an Inject in term of security posture. 

Expectations can be:
- Manual: the expectation is at the end of the animation team. its validation and score will be manually handled
- Automatic: the expectation's validation will be handled automatically under specific conditions

Current available expectations are:
- "Manual": a simple customizable expectation to be validated manually.
- "Automatic - Prevention:Triggred when inject is processed": Automatically validated by security integration with compatible Collectors if the Inject's action generates a prevention alert, like a quarantine.
- "Automatic - Detection:Triggred when inject is processed": Automatically validated by security integration with compatible Collectors if the Inject's action generates a detection alert, like an incident.
- "Automatic - Triggered when team reads articles": Automatically validated when the article of a Media presssure inject has been read by targets.


Note that the "Publish Challenges" Inject does not need Expectation. Results are computed directly from Challenges' scores.

Each expectation has a score that represent how well the expectation has been met by the target.

Some expectations can be validated in two mode when an Inject is targeting Asset groups:
- All assets (per group) must validate the expectation
- At least one asset (per group) must validate the expectation

<!-- screenshot of an expectation form -->

## Add an expectation to an inject

For adding expectations to an Inject, open the Inject content and click on "Add expectations". Then select the type of expectation and set a score for it.

You can add multiple expectations to an inject.

## Validate a manual expectation

If you have configured manual expectations in your Scenario, you will have the opportunity to handle manual validations during each of its Simulation. During a Simulation, go to the Animation tab, under the Validation screen. You will see there the list of expectations that still need a manual validation.

<!-- screenshot of the screen populated with manual validation to perform -->

