# Media pressure
!!! tip "Under construction"

    We are doing our best to complete this page. 
    If you want to participae, dont hesitate to join the [Filigran Community on Slack](https://community.filigran.io) 
    or submit your pull request on the [Github doc repository](https://github.com/OpenBAS-Platform/docs).


Media pressure are Articles or web contents you create to give more shape to your Scenario, or to simulate contextual pressure on your Teams and Players.

For example, you can create an Article about the data leakage your organization is said to be affected by during the Scenario, and simulate its publishing by a large coverage media outlet with a "Publish channel pressure" inject.


## Create an Article

To create an Article, go to the definition page of your Scenario or your Simulation and click on the + button near "media pressure". If you did not create Article yet, you can also click on the more visible "Create an Article" button.

A media pressure Article is defined by:
- Channel: the [Channel](channels.md) template that will shape the article during the display to the Players. A Channel must have been defined in the platform.
- Title
- Author
- Content: the content of your article. You can enrich the text and have a preview of the formatted result. You can also go fullscreen.
- To simulate social network engagement, you can define number of comments, Shares and Likes of the Articles.
- Documents: you can attach file to the Article. It can be useful if you want to simulate the publication of a large report you don't want to craft inside OpenBAS, like a pdf security report for example.

Once created, Articles appears as cards in the definition screen of the Scenario or Simulation they have been created into. Note that if an article is not yet used in the Scenario or Simulation (probably because it does not have been used in a "Publish channel pressure" inject), it is mentioned into the Article's card.

<!-- Capture of the Article creation screen -->


## Use an Article in Scenario and Simulation

To use an Article in a Scenario or Simulation, it must have been created in the context of the Scenario, the Simulation's parent Scenario or the Simulation itself.

When you select an Inject, if it is compatible with media pressures, you can add a media pressure article to it.

<!-- capture of the inject creation screen with a media pressure on it -->

