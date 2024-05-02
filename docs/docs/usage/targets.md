# Targets


!!! tip "Under construction"

    We are doing our best to complete this page. If you want to participate, don't hesitate to join the [Filigran Community on Slack](https://community.filigran.io) or submit your pull request on the [Github doc repository](https://github.com/OpenBAS-Platform/docs).


When you are using an Inject, either for Atomic testing, Scenario or Simulation, you need to define which Players, Teams, Assets (endpoints) or/and Asset groups it will be sent to. They are called "targets" of the Inject.

Note that some Injects cannot target Assets and other cannot target Players. For example, it is not possible to target an Asset with the Inject "Send individual mails", only Players and Teams.

Selecting Targets for an Inject is done when creating or updating an Inject. 

<!-- screenshot of an inject contract -->

## Selecting Players and Teams

It is not yet possible to target directly a Player. You must target a Team. If you are into a Scenario or a Simulation, the Team must be part of the [Scenario](scenario.md) or Simulation to be selectable. If you are creating an Atomic testing, all Teams in the platform are selectable.

Note that visibility of Teams and Players is limited by the Organization's [segregation](teams_and_players_and_organizations.md).

When selecting a Team to be targeted, all Players of the Team will be targeted by the inject. Each one will have to complete assorted expectations.

<!-- screenshot of an innject with selected teams -->

## Selecting Assets (endpoints) and Asset Groups

You can target Assets (endpoints) directly or Asset groups. In the dedicated dialog, only Assets compatible with the Inject are listed by default. 

When selecting an Asset group to be targeted, all Assets (endpoints) of the group will be targeted by the Inject. Each one will have to complete assorted expectations.

<!-- screenshot with selected assets -->