package io.openaev.utils.fixtures;

import io.openaev.database.model.Channel;
import java.util.UUID;

public class ChannelFixture {
  public static final String CHANNEL_NAME = "Very respected publication";

  public static Channel getDefaultChannel() {
    Channel channel = createChannelWithDefaultName();
    channel.setType("Journal");
    channel.setDescription("This is a very respected publication and not at all slanderous");
    return channel;
  }

  public static Channel getChannel() {
    Channel channel = createChannelWithName(CHANNEL_NAME);
    channel.setType("Journal");
    channel.setDescription("This is a very respected publication and very serious");
    return channel;
  }

  private static Channel createChannelWithDefaultName() {
    return createChannelWithName(null);
  }

  private static Channel createChannelWithName(String name) {
    String new_name = name == null ? "channel-%s".formatted(UUID.randomUUID()) : name;
    Channel channel = new Channel();
    channel.setName(new_name);
    return channel;
  }
}
