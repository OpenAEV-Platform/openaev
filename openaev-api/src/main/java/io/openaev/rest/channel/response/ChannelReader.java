package io.openaev.rest.channel.response;

import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Article;
import io.openaev.database.model.Channel;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelReader {

  @Setter(NONE)
  @JsonProperty("channel_id")
  private String id;

  @JsonProperty("channel_information")
  private Channel channel;

  @JsonProperty("channel_exercise")
  private Exercise exercise;

  @JsonProperty("channel_scenario")
  private Scenario scenario;

  @JsonProperty("channel_articles")
  private List<Article> channelArticles = new ArrayList<>();

  public ChannelReader(Channel channel, Exercise exercise) {
    this.id = channel.getId();
    this.channel = channel;
    this.exercise = exercise;
  }

  public ChannelReader(Channel channel, Scenario scenario) {
    this.id = channel.getId();
    this.channel = channel;
    this.scenario = scenario;
  }
}
