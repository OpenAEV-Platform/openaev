package io.openaev.service;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable snapshot of the RabbitMQ broker connection details.
 *
 * <p>Returned by {@link RabbitmqService#getConnectionInfo()} so that callers can obtain connection
 * metadata without depending on {@link io.openaev.config.RabbitmqConfig} directly.
 *
 * <p>This record is also serialized as part of the injector registration response, providing
 * external injectors with the credentials they need to connect to the broker.
 *
 * @param hostname the broker hostname
 * @param vhost the virtual host
 * @param ssl whether SSL/TLS is enabled
 * @param port the AMQP port
 * @param user the authentication username
 * @param pass the authentication password
 */
public record BrokerConnectionInfo(
    @JsonProperty("host") String hostname,
    @JsonProperty("vhost") String vhost,
    @JsonProperty("use_ssl") boolean ssl,
    @JsonProperty("port") int port,
    @JsonProperty("user") String user,
    @JsonProperty("pass") String pass) {}
