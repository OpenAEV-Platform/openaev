package io.openaev.api.news_feed;

import io.openaev.xtmhub.NewsFeedSettings;

public class NewsFeedSettingsMapper {

  private NewsFeedSettingsMapper() {}

  public static NewsFeedSettingsOutput toOutput(NewsFeedSettings settings) {
    return new NewsFeedSettingsOutput(
        settings.subscribedTypes(), settings.hubRegistered(), settings.hubConnected());
  }
}
