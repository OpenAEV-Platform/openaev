# Scenario

When clicking on Scenarios in the left menu, you access to the list of all Scenarios defined in the platform. Scenarios act as templates that translate threat contexts into meaningful events to simulate.

Scenarios can be grouped by defining categories, main focus, severity and tags. It is then possible to filter the Scenarios list based on these attributes. Quick filters are available by default at the top of the screen to filter Scenario on most used categories.

It is also possible to search Scenarios by their names using the search bar.

<!-- screenshot du screen de la liste de scenarios -->


## Create a scenario

To create a scenario, hit the + button on the bottom right of the screen and define general metadata that make sense for you. 

Once done, the scenario is accessible in the list. Click on it to see its details and define them.


## Scenario overview

The overview provides comprehensive information for evaluating your security posture against the threat context over time. It displays the scenario's context along with the results of the simulations played from it. Additionally, the list of these simulations is shown, allowing easy access to their specific details and results.

If the scenario has never been simulated, the results' widget contains an example of how results will be displayed, and the list of simulations is replaced with an invitation to generate one.

<!-- screenshot de l'overview d'un scenario simulé plusieurs fois -->


## Defining a Scenario

To define the scenario, navigate to the "Definition" and "Injects" tabs.

In the "Definition" tab, you can add various elements to construct events:

- [Teams and Players](people.md) involved in the scenario,
- [Custom variables](components/variables.md) for simplifying injects' customization,
- Articles that you might use to simulate [media pressure](components/media_pressure.md),
- [Challenges](components/challenges.md) designed for including Capture The Flag elements in your scenario.

<!-- screenshot of the Definition screen with elements inside -->

Once you have added all the elements you need, you can go to the "Injects" tab to begin to create the chain of events that will shape your scenario.

By clicking on the + button at the bottom right of the screen, you enter the [inject creation workflow](injects.md#Inject-creation-process).


## Launching a simulation of the scenario

Once you've finished defining your scenario, it's time to simulate it and evaluate your security posture!

To do so, click the "Simulate now" button. You can choose to simulate this scenario as a one-time evaluation, scheduling it for a specific date and time. Additionally, you can set it to simulate recurrently to assess your posture over time. The results of each simulation will populate your scenario overview.

A visual indication, located to the right of the scenario's title, provides a quick way to know if the scenario is currently being simulated.


## Export your scenario

You can export your carefully crafted scenario as a JSON file by clicking the three-dots button at the top right of the scenario screen. This allows you to share it with others, or store it outside the platform.
