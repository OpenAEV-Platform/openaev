package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class QueueConfig {
  @JsonProperty("publisher-number")
  private int publisherNumber = 1;

  @JsonProperty("consumer-number")
  private int consumerNumber = 1;

  @JsonProperty("worker-number")
  private int workerNumber = 1;

  @JsonProperty("worker-frequency")
  private int workerFrequency = 10000;

  @JsonProperty("queue-name")
  private String queueName = "openaev-queue";

  @JsonProperty("max-size")
  private int maxSize = 100;

  @JsonProperty("consumer-qos")
  private int consumerQos = 30;

  @JsonProperty("publisher-qos")
  private int publisherQos = 30;
}
