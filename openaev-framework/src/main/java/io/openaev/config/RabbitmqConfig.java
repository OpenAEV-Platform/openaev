package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openaev.rabbitmq")
@Data
public class RabbitmqConfig {

  @JsonProperty("rabbitmq_prefix")
  private String prefix;

  @JsonProperty("rabbitmq_hostname")
  private String hostname;

  @JsonProperty("rabbitmq_vhost")
  private String vhost;

  @JsonProperty("rabbitmq_ssl")
  private boolean ssl;

  @JsonProperty("rabbitmq_port")
  private int port;

  @JsonProperty("rabbitmq_management-port")
  private int managementPort;

  @JsonProperty("rabbitmq_user")
  private String user;

  @JsonProperty("rabbitmq_pass")
  private String pass;

  @JsonProperty("rabbitmq_queue-type")
  private String queueType;

  @JsonProperty("rabbitmq_management-insecure")
  private boolean managementInsecure;

  @JsonProperty("rabbitmq_trust-store-password")
  private String trustStorePassword;

  @Value("${openaev.rabbitmq.trust.store}")
  private Resource trustStore;
}
