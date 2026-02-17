package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Configuration class for queue processing settings.
 *
 * <p>This class defines parameters for message queue operations including:
 *
 * <ul>
 *   <li>Publisher/consumer thread counts
 *   <li>Worker configuration for background processing
 *   <li>Quality of Service (QoS) settings for flow control
 *   <li>Queue sizing parameters
 * </ul>
 */
@Data
public class QueueConfig {

  /** Number of publisher threads (default: 1). */
  @JsonProperty("publisher-number")
  private int publisherNumber = 1;

  /** Number of consumer threads (default: 1). */
  @JsonProperty("consumer-number")
  private int consumerNumber = 1;

  /** Number of worker threads for background processing (default: 1). */
  @JsonProperty("worker-number")
  private int workerNumber = 1;

  /** Worker polling frequency in milliseconds (default: 10000). */
  @JsonProperty("worker-frequency")
  private int workerFrequency = 10000;

  /** Name of the message queue (default: "openaev-queue"). */
  @JsonProperty("queue-name")
  private String queueName = "openaev-queue";

  /** Maximum number of messages in the queue (default: 100). */
  @JsonProperty("max-size")
  private int maxSize = 100;

  /** Consumer prefetch count for flow control (default: 30). */
  @JsonProperty("consumer-qos")
  private int consumerQos = 30;

  /** Publisher confirmation batch size (default: 30). */
  @JsonProperty("publisher-qos")
  private int publisherQos = 30;
}
