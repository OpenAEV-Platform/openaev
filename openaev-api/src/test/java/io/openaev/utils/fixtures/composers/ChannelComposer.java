package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.Channel;
import io.openaev.database.repository.ChannelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChannelComposer extends ComposerBase<Channel> {
  @Autowired private ChannelRepository channelRepository;

  public class Composer extends InnerComposerBase<Channel> {
    private final Channel channel;

    public Composer(Channel channel) {
      this.channel = channel;
    }

    @Override
    public Composer persist() {
      channelRepository.save(channel);
      return this;
    }

    @Override
    public Composer delete() {
      channelRepository.delete(channel);
      return this;
    }

    public Composer withId(String id) {
      this.channel.setId(id);
      return this;
    }

    @Override
    public Channel get() {
      return channel;
    }
  }

  public Composer forChannel(Channel channel) {
    generatedItems.add(channel);
    return new Composer(channel);
  }
}
