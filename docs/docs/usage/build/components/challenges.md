# Challenges

Challenges embed CTF (Capture The Flag) activities into your Simulations. You define a challenge with one or more flags that Players must find, and OpenAEV tracks who solved it and when.

## Why use Challenges?

- Test Player skills with hands-on security exercises inside a Simulation.
- Score and rank Player responses automatically.
- Combine Challenges with other Inject types (email, media pressure) for realistic exercise scenarios.

## Create a Challenge

To create a new challenge, follow these steps:

1. Click the + button at the bottom right corner of the screen.
2. Give your new challenge a name and specify one or more flags.
3. Optionally, fill in additional fields to provide more context to your players, such as the category, content (
   explanation, context, steps), and attach any relevant documents.
4. Manage your challenge by setting a score and a maximum number of attempts allowed for completing the challenge.

![challenge-creation.png](./assets/challenge-creation.png)

Once completed, your new challenge will appear in the challenge list.

## Use a challenge

Challenges can be utilized in Scenarios and Simulations. When creating an Inject of type "Publish challenges," you need
to select a Challenge to be sent to your Players.

![challenge-inject.png](../../assets/components/challenge-inject.png)

Once the scenario/simulation is triggered, an email is sent to the targeted players with a link.

![challenge-player-response.png](../../assets/components/challenge-player-response.png)

When clicking on the link the player is redirected to a page with a clickable card. Clicking on the card opens a modal with a textfield, where the player can enter the flag of the challenge.

![challenge-admin-answers.png](../../assets/components/challenge-admin-answers.png)

The initiator of the simulation can check the results. On this picture, a player of the team answered correctly while the others have not answered yet. Note that by default, players have a day to respond.

## What's next?

- [Scenarios](../scenario/scenario.md) -- Use Challenges in your Scenarios
- [Built-in Injects](../../environment/injects-builtin.md) -- Overview of all built-in Injectors including the Challenge Injector
- [Media pressure](media-pressure.md) -- Simulate media coverage during exercises
