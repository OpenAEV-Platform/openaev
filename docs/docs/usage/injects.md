# Injects

## Types inject

An inject is a one-time event that is played as part of your exercise.

Several injects are available in the OpenBAS platform:

- Challenge: allows to publish challenges to be realized by players
- Email: allows to send emails to players
- HTTP request: allows executing HTTP requests (GET/POST/PUT) on external services
- Manual: allows to create a reminder for the animation team to carry out manually, outside the platform
- Mastodon: allows to publish a message on a mastodon account
- Media pressure: allows to publish an article on a predefined fake platform media
- SMS(OVH): allows to send text messages to players

<a id="manual-section"></a>
### Manual inject

The manual inject is like a silent reminder. It allows you to place in the timeline a stimulus to be produced manually, outside the platform (for example simulated a call from a journalist on the switchboard telephone).

<a id="http-section"></a>
## HTTP inject

The HTTP inject is used to forge an HTTP request to a third party services in order to perform actions outside the platform (for example API call to an EDR).

## Expectations

Some injects can define expectations from players in order to define actions directly played by the players and those simulated by the animation team.

### Manual expectations

Email, SMS or Media Pressure type inject can have manual expectations. This allows to create expectations which will be validated once the inject has been executed.

![Add expectation manual](assets/add-expectation-manual.png)
![Expectation manual](assets/expectation-manual.png)

<a id="media-pressure-section"></a>
### Media pressure expectation

This allows to create an expectation which will be validated once the audience concerned by the inject has read the article(s).

![Add expectation media](assets/add-expectation-media.png)
![Expectation media](assets/expectation-media.png)

<a id="challenge-section"></a>
### Challenge expectation

An expectation is automatically created and it will be validated once the audience concerned by the inject has completed the challenge.

### Validation of expectations

These expectations can be viewed and validated, in the case of manual expectations, in a dedicated view.

![Expectation validation](assets/expectation-validation.png)
![Expectation validation manual](assets/expectation-validation-manual.png)
