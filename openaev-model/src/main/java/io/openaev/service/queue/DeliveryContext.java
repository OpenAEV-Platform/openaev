package io.openaev.service.queue;

import com.rabbitmq.client.Channel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryContext {

  private long tag;

  private Channel deliveryChannel;
}
