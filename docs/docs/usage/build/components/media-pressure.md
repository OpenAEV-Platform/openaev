# Media pressure

Media pressure consists of articles or web content you create to give context to your Scenario or to simulate pressure on your Teams and Players. For example, you can create an article about a data breach affecting your organization and simulate its publication by a news outlet using a "Publish channel pressure" Inject.

## Why use media pressure?

Media pressure adds realism to Simulations by introducing contextual information that participants must process alongside technical events. It helps evaluate how Teams respond to external pressure such as media coverage, social media activity, or internal communications.

## Create an article

To create an article:

1. Open the definition page of your Scenario or Simulation.
2. Click the **+** button next to "media pressure". If no articles exist yet, click the **Create an article** button.
3. Fill in the article fields:

| Field | Description |
|---|---|
| Channel | The [Channel](channels.md) template that defines the article's visual appearance. A Channel must already exist in the platform. |
| Title | The article headline |
| Author | The author name displayed on the article |
| Content | Rich text content with formatting and preview. Supports fullscreen editing. |
| Comments | Simulated social engagement count |
| Shares | Simulated share count |
| Likes | Simulated like count |
| Documents | Optional file attachments (e.g., a PDF report) |

Once created, articles appear as cards on the Scenario or Simulation definition screen. If an article is not yet used in any Inject, a notice is displayed on the card.

## Use an article in a Scenario or Simulation

To publish an article during a Simulation:

1. Create a "Publish channel pressure" Inject in your Scenario or Simulation timeline.
2. Select the article(s) to publish.
3. When the Inject executes, the articles appear in the Player interface.

!!! note

    An article can only be used in the Scenario where it was created, in a Simulation that belongs to that Scenario, or in the Simulation itself if the article was created directly there.

## What's next?

- [Channels](channels.md) -- Define Channel templates for article appearance
- [Inject overview](../../evaluate/injects/inject-overview.md) -- Create and configure Injects
- [Built-in Injects](../../environment/injects-builtin.md) -- Overview of all built-in Injectors
