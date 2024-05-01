# Scenario
!!! tip "Under construction"

    We are doing our best to complete this page. 
    If you want to participae, dont hesitate to join the [Filigran Community on Slack](https://community.filigran.io) 
    or submit your pull request on the [Github doc repository](https://github.com/OpenBAS-Platform/docs).



When clicking on Scenarios in the left menu, you access to the list of all Scenarios that have been defined in the platform. Scenario are like templates that translate threat context into meaningfull events to simulate.

Scenarios can be categorized by defining categories, main focus, severity and tags. It is then possible to filter the Scenarios list based on this attributes. Quick filters are present by default on top of the screen to filter Scenario on most used Categories.

It is also possible to search Scenarios by their names using the contextual search bar.

<!-- screenshot du screen de la liste de scenarios -->


## Create a Scenario

To create a Scenario, hit the + button on the bottom right of the screen and define general metadata that make sense for you. 

Once done, the Scenario is accessible in the list. Click on it to see its details and define them.

## Scenario Overview

The Overview regroups everything you need to know to evaluate you security posture facing this threat context over time. The context of the Scenario is displayed, with all the Results of the simulations that have been played from this Scenarios. The list of these Simulations is also displayed to easily switch to their specific details and results.

If the Scenario has never been simulated, note that the Results' widgets contain an example of how results will be displayed and the list of Simulations is replaced with a invite to generate one.

<!-- screenshot de l'overview d'un scenario simulé plusieurs fois -->

## Defining a Scenario

Of course, simulating an empty Scenario is useless. To define the Scenario, use the "Definition" and "Injects" screens.

In Definition, you can add all elements that will help you to construct events:
- [Teams and Players](teams_and_players_and_organizations.md) involved in the Scenario
- Custom [Variables](variables.md) you may need to simplify Injects' customization
- Articles you may used in the Scenario to simulate [media pressure](media_pressure.md)
- [Challenges](challenges.md) you may have designed if you including Capture The Flag elements in your Scenario

<!-- screenshot of the Definition screen with elements inside -->

Once you have added all the elements you may need, you can go to the [Injects](injects.md) screen to begin to create the chain of events that will shape your Scenario.

By clicking on the + button at the bottom right of the screen, you enter the inject creation workflow.

On the left of the creation screen is the list of all available Inject you can add to your Scenario. Logos on the left of each line indicates which Injector is associated with each [inject](injects.md).

Depending on your integrations, this list can be long. You can filter the list by compatible platforms or by Mitre Att&ck tactics. By clicking on the "Att&CK" logo near the search bar, you can also filter by selecting a precise Mitre Att&ck techniques.

When selecting an inject on the left, the form on the right populates itself with a by-default title and propose you to define when the inject should be played in the Scenario's chronology.

By clicking on Inject content, you can define now or later the targeted assets and players, needed configurations, and the assorted expectations.

The "available variables" button helps you to use already defined variables into compatible fields.

## Launching a Simulation of the Scenario

When you have finished to define your Scenario, it is time to simulate it and evaluate your security posture!

To do so, hit the Simulate now button. You can decide to simulate this Scenario as a one-time evaluation, as the date and time you see fit. And you can also simulate it recurringly to evaluate your posture over time. You will see Results of each Simulation populate your Scenario overview.

A visual indication, at the right of the Scenario's title, helps you quicly know if the Scenario is being simulated or not.

## Export your Scenario

You have the capability to export your carefully crafted Scenario as a JSON file by hitting the three-dots button on the top right of the Scenario screen. it can help to share it with others, or to store it outside the platform.