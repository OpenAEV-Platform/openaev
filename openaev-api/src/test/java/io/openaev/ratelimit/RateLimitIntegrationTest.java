package io.openaev.ratelimit;

import static io.openaev.ratelimit.config.Limits.REFILL_PERIOD_1000MS;
import static io.openaev.ratelimit.support.ThrottledEndpoint.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.ratelimit.config.RateLimitConfig;
import io.openaev.utils.mockConfig.WithMockRateLimitConfig;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockRateLimitConfig(enabled = true)
public class RateLimitIntegrationTest extends IntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private RateLimitConfig rateLimitConfig;

  @FunctionalInterface
  interface VoidFunction {
    void execute(int i) throws Exception;
  }

  private void loopCalls(Long iterations, VoidFunction func) throws Exception {
    for (int i = 0; i < iterations; i++) {
      func.execute(i);
    }
  }

  @BeforeEach
  void before() throws InterruptedException {
    // Force the test harness to wait for the bucket refills between tests.
    // This allows using the same backend process for all tests, avoiding
    // a context rebuild, thus globally speed up this test suite.
    Thread.sleep(REFILL_PERIOD_1000MS);
  }

  @Test
  @DisplayName("Bucket is refilled after period")
  void bucketIsRefilledAfterPeriod() throws Exception {
    Long boundary = rateLimitConfig.getDefaultRps();
    VoidFunction v =
        (i) ->
            mvc.perform(get(AUTHED_SIMPLE_GET).contentType(MediaType.APPLICATION_JSON).with(csrf()))
                .andExpect(
                    boundary.equals((long) i)
                        ? status().isTooManyRequests()
                        : status().isUnauthorized());

    loopCalls(boundary + 1, v);
    Thread.sleep(REFILL_PERIOD_1000MS);
    loopCalls(boundary + 1, v);
  }

  @Nested
  @DisplayName("When unauthenticated")
  class WhenUnauthenticated {
    @Test
    @DisplayName("Calls are throttled on open endpoint")
    void callsThrottledOnOpenEndpoint() throws Exception {
      loopCalls(
          rateLimitConfig.getDefaultRps() + 1,
          (i) ->
              mvc.perform(get(OPEN_SIMPLE_GET).contentType(MediaType.APPLICATION_JSON).with(csrf()))
                  .andExpect(
                      rateLimitConfig.getDefaultRps().equals((long) i)
                          ? status().isTooManyRequests()
                          : status().isOk()));
    }

    @Test
    @DisplayName("Calls are throttled on protected endpoint")
    void callsThrottledOnProtectedEndpoint() throws Exception {
      loopCalls(
          rateLimitConfig.getDefaultRps() + 1,
          (i) ->
              mvc.perform(
                      get(AUTHED_SIMPLE_GET).contentType(MediaType.APPLICATION_JSON).with(csrf()))
                  .andExpect(
                      rateLimitConfig.getDefaultRps().equals((long) i)
                          ? status().isTooManyRequests()
                          : status().isUnauthorized()));
    }

    @Test
    @DisplayName("Calls are throttled on protected endpoint with failed auth")
    void callsThrottledOnProtectedEndpointWithFailedAuth() throws Exception {
      loopCalls(
          rateLimitConfig.getDefaultRps() + 1,
          (i) ->
              mvc.perform(
                      get(AUTHED_SIMPLE_GET)
                          .header(HttpHeaders.AUTHORIZATION, "Bearer will_fail_auth")
                          .contentType(MediaType.APPLICATION_JSON)
                          .with(csrf()))
                  .andExpect(
                      rateLimitConfig.getDefaultRps().equals((long) i)
                          ? status().isTooManyRequests()
                          : status().isUnauthorized()));
    }
  }

  @Nested
  @DisplayName("When authenticated")
  @WithMockUser(withCapabilities = {Capability.AGENT_RUNTIME_ACCESS})
  class WhenAuthenticated {
    @Test
    @DisplayName("Calls are throttled on open endpoint")
    void callsThrottledOnOpenEndpoint() throws Exception {
      loopCalls(
          THROTTLED_ENDPOINT_AUTHED_RPS + 1,
          (i) ->
              mvc.perform(get(OPEN_SIMPLE_GET).contentType(MediaType.APPLICATION_JSON).with(csrf()))
                  .andExpect(
                      i == THROTTLED_ENDPOINT_AUTHED_RPS
                          ? status().isTooManyRequests()
                          : status().isOk()));
    }

    @Test
    @DisplayName("Calls are throttled on protected endpoint")
    void callsThrottledOnProtectedEndpoint() throws Exception {
      loopCalls(
          THROTTLED_ENDPOINT_AUTHED_RPS + 1,
          (i) ->
              mvc.perform(
                      get(AUTHED_SIMPLE_GET).contentType(MediaType.APPLICATION_JSON).with(csrf()))
                  .andExpect(
                      i == THROTTLED_ENDPOINT_AUTHED_RPS
                          ? status().isTooManyRequests()
                          : status().isOk()));
    }
  }
}
