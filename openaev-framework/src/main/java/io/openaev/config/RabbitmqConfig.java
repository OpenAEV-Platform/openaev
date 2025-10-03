package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@Data
public class RabbitmqConfig {
  @JsonProperty("rabbitmq_prefix")
  @Value("${openbas.rabbitmq.prefix:${openaev.rabbitmq.prefix:#{null}}}")
  private String prefix;

  @JsonProperty("rabbitmq_hostname")
  @Value("${openbas.rabbitmq.hostname:${openaev.rabbitmq.hostname:#{null}}}")
  private String hostname;

  @JsonProperty("rabbitmq_vhost")
  @Value("${openbas.rabbitmq.vhost:${openaev.rabbitmq.vhost:#{null}}}")
  private String vhost;

  @JsonProperty("rabbitmq_ssl")
  @Value("${openbas.rabbitmq.ssl:${openaev.rabbitmq.ssl:false}}")
  private boolean ssl;

  @JsonProperty("rabbitmq_port")
  @Value("${openbas.rabbitmq.port:${openaev.rabbitmq.port:5672}}")
  private int port;

  @JsonProperty("rabbitmq_management-port")
  @Value("${openbas.rabbitmq.management-port:${openaev.rabbitmq.management-port:15672}}")
  private int managementPort;

  @JsonProperty("rabbitmq_user")
  @Value("${openbas.rabbitmq.user:${openaev.rabbitmq.user:#{null}}}")
  private String user;

  @JsonProperty("rabbitmq_pass")
  @Value("${openbas.rabbitmq.pass:${openaev.rabbitmq.pass:#{null}}}")
  private String pass;

  @JsonProperty("rabbitmq_queue-type")
  @Value("${openbas.rabbitmq.queue-type:${openaev.rabbitmq.queue-type:#{null}}}")
  private String queueType;

  @JsonProperty("rabbitmq_management-insecure")
  @Value("${openbas.rabbitmq.management-insecure:${openaev.rabbitmq.management-insecure:false}}")
  private boolean managementInsecure;

  @JsonProperty("rabbitmq_trust-store-password")
  @Value(
      "${openbas.rabbitmq.trust-store-password:${openaev.rabbitmq.trust-store-password:#{null}}}")
  private String trustStorePassword;

  @Value("${openbas.rabbitmq.trust.store:${openaev.rabbitmq.trust.store:#{null}}}")
  private Resource trustStore;
}
