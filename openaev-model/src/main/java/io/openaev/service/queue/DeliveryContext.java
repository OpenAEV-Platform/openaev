package io.openaev.service.queue;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.Builder;
import lombok.Data;

/** A RabbitMQ delivery waiting for its answer: its tag only means something on its channel */
@Data
@Builder
public class DeliveryContext {

  private long tag;

  private Channel deliveryChannel;

  /** Tell RabbitMQ this delivery was processed, so it is removed from the queue */
  public void ack() throws IOException {
    deliveryChannel.basicAck(tag, false);
  }

  /** Tell RabbitMQ this delivery failed and drop it: it is not requeued, so it is never retried */
  public void rejectWithoutRequeue() throws IOException {
    deliveryChannel.basicReject(tag, false);
  }
}
