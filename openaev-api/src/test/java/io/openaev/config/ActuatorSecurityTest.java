package io.openaev.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.health.DependencyHealth;
import io.openaev.health.DependencyHealthStore;
import io.openaev.health.PlatformDependency;
import io.openaev.health.StorageUsage;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Prometheus scrape is served on the public API port, so the scrape key is the only thing
 * standing between the internet and the platform internals.
 */
// @SpringBootTest disables metrics export by default (it forces
// management.defaults.metrics.export.enabled=false), which would leave the scrape endpoint
// unmapped and make this class assert a 404 that production never returns.
@AutoConfigureObservability
@TestPropertySource(
    properties = {
      "management.endpoints.web.exposure.include=prometheus",
      "openaev.metrics.key=" + ActuatorSecurityTest.SCRAPE_KEY
    })
@DisplayName("Actuator security")
class ActuatorSecurityTest extends IntegrationTest {

  static final String SCRAPE_KEY = "a-scrape-key";

  private static final String PROMETHEUS_URI = "/actuator/prometheus";

  @Autowired private MockMvc mvc;
  @Autowired private DependencyHealthStore dependencyHealthStore;

  @BeforeEach
  void seedProbeResults() {
    dependencyHealthStore.record(
        PlatformDependency.POSTGRESQL, DependencyHealth.up(Instant.now(), Duration.ofMillis(1)));
    dependencyHealthStore.recordStorageUsage(new StorageUsage(1L, 2L, 3L));
  }

  @DisplayName("Given no scrape key, should reject the request")
  @Test
  void given_no_scrape_key_should_reject_the_request() throws Exception {
    mvc.perform(get(PROMETHEUS_URI)).andExpect(status().isUnauthorized());
  }

  @DisplayName("Given a wrong scrape key, should reject the request")
  @Test
  void given_a_wrong_scrape_key_should_reject_the_request() throws Exception {
    mvc.perform(get(PROMETHEUS_URI).header(HttpHeaders.AUTHORIZATION, "Bearer wrong"))
        .andExpect(status().isUnauthorized());
  }

  @DisplayName("Given the scrape key, should expose the dependency and storage metrics")
  @Test
  void given_the_scrape_key_should_expose_the_metrics() throws Exception {
    mvc.perform(get(PROMETHEUS_URI).header(HttpHeaders.AUTHORIZATION, "Bearer " + SCRAPE_KEY))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("openaev_dependency_up")))
        .andExpect(content().string(containsString("openaev_storage_used_bytes")));
  }

  @DisplayName("Given an unexposed actuator endpoint, should answer 404 rather than serve the SPA")
  @Test
  void given_an_unexposed_actuator_endpoint_should_answer_404() throws Exception {
    // The SPA catch-all otherwise swallows /actuator/* into a 200 text/html, which makes a
    // misconfigured scrape look like a working page.
    mvc.perform(
            get("/actuator/env")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SCRAPE_KEY)
                .accept(MediaType.TEXT_HTML))
        .andExpect(status().isNotFound());
  }
}
