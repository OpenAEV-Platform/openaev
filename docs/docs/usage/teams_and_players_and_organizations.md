# Players, Teams and Organizations
!!! tip "Under construction"

    We are doing our best to complete this page. 
    If you want to participae, dont hesitate to join the [Filigran Community on Slack](https://community.filigran.io) 
    or submit your pull request on the [Github doc repository](https://github.com/OpenBAS-Platform/docs).


Breach and Attack simulation is about testing your security posture, and People are an essential part of it!

Players, Teams and Organizations are where you organize  into OpenBAS all the human part of your security posture. Ones you will target with injects during your [Simulations](simulation.md) and [Atomic testings](atomic.md).

## Players

Players are the users that may take part into your Scenarios, to be tested against attack or contextual events ([Injects](injects.md)).

Players can be created manually with the + button at the bottom right, but we encourage you to active an integration allowing to improt them from your IT environnement, like with Microsoft Entra integration.

Players are defined by:
- Email address
- First name
- Last name
- Organization: you can link a Player to an Organization (see below)
- Country
- Phone number
- PGP Public key: necessary if you want to play encrypted email injects
- Tags: if you want to sort them by custom categories

This list of Players can be exported by clicking on the export button, at the top right of the players screen.

<!-- no screenshot here expect if all Players are fake people -->

## Teams

Teams regroup Players into groups that can be targeted by injects during Simulations or Atomic testing. It is the best way to represent your different security team (like CSIRT, SOC, VOC, etc.) and other teams that might be involved into your Scenario (legal department, communication department, etc.)

Teams are defined by:
- Name
- Description
- Organization: you can link a Team to an Organization (see below)
- Tags: if you want to sort them by custom categories

From the Teams list, you can manage Players of a Team by clicking on the three-dots inline button on the right and select "Manage Players". From there, you can see all Players of the Teams and their communication channels' state. You can update, remove or delete them.

<!-- screenshot of the Teams list  with a Manage Players drawer opened -->

## Organizations

Organization is a simple way to segregate Players and Teams in the platform. A Player associated with an Organization, even with the necessary rights to animate and planned Scenarios and Simulations, will never see Players and Teams from other Organizations.

It can be very handfull if you are using the OpenBAS platform for planning and playing Simulations for different companies or subsidiaries.

