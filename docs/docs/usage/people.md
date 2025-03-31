# People

Breach and Attack Simulation involves testing your security posture, and people are an essential part of it!

Players, teams, and organizations are where you organize the human aspect of your security posture within OpenBAS. These
entities are the targets for injects during your [simulations](simulation.md) and [atomic testings](atomic.md).

## Players

Players are the users that may take part into your scenarios, to be tested against attack or contextual events (
i.e. [injects](injects.md)).

Players can be created manually with the + button at the bottom right, but we encourage you to activate an integration
allowing to import them from your IT environment, like with Microsoft Entra integration.

Players are defined by:

- Email address,
- First name,
- Last name,
- Organization: to link a player to an organization (
  see [below](#organizations)),
- Country,
- Phone number: necessary if you want to play SMS injects,
- PGP public key: necessary if you want to play encrypted email injects,
- Tags: if you want to sort them by custom categories.

This list of players can be exported by clicking on the export button, at the top right of the players screen.

<!-- no screenshot here expect if all Players are fake people -->

## Teams

Teams group players into units that can be targeted by injects during simulations or atomic testing. They serve as a way
to represent different security teams (e.g., CSIRT, SOC, VOC) and other relevant teams that might be involved into your
scenario (e.g., legal department, communication department).

Teams are defined by:

- Name,
- Description,
- Organization: to link a team to an organization (
  see [below](#organizations)),
- Tags: if you want to sort them by custom categories.

From the teams list, you can manage players by clicking on the three-dots inline button on the right and selecting "
Manage players." From there, you can view, update, or delete all the team's players and see their communication
channels' state.

<!-- screenshot of the Teams list  with a Manage Players drawer opened -->

## Organizations

Organization provides a straightforward method to segregate players and teams within the platform. A player associated
with an organization, even with the required rights to animate and planned scenarios and simulations, will never see
players and teams from other organizations.

This feature can be particularly useful if you are using OpenBAS to plan and execute simulations for various companies
or subsidiaries.

