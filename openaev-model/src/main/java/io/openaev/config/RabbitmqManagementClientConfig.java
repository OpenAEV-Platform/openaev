package io.openaev.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Produces the {@link HttpClient} bean used by RabbitmqManagementClient. */
@Configuration
public class RabbitmqManagementClientConfig {

  @Bean
  public HttpClient rabbitmqManagementHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
  }
}
