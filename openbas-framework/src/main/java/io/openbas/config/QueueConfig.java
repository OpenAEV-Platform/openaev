package io.openbas.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class QueueConfig {
  @JsonProperty("consumer-number")
  private int consumerNumber;

  @JsonProperty("consumer-frequency")
  private int consumerFrequency;

  @JsonProperty("queue-name")
  private String queueName;

  @JsonProperty("max-size")
  private int maxSize;

  @JsonProperty("consumer-qos")
  private int consumerQos;

  @JsonProperty("publisher-qos")
  private int publisherQos;
}
