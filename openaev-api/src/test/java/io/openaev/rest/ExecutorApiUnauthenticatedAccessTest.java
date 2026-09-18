package io.openaev.rest;

import static io.openaev.rest.executor.ExecutorApi.AGENT_URI;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Endpoint;
import io.openaev.service.EndpointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code /api/agent/**} is permitAll at the Spring Security filter-chain level (agents call these
 * before they hold any session), so the only gate left is {@code @AccessControl(...
 * AGENT_INSTALLER)} resolving a principal from the request's own bearer token. This class
 * intentionally carries no {@code @WithMockUser} (class or method) so requests here run as
 * whatever the filter chain assigns a truly anonymous caller — reproducing an install script that
 * sends no Authorization header at all, which is what broke the infra-agent/infra-multitenant E2E
 * specs: the bundled installer script's internal executable download has no auth header and was
 * getting a 401 from this exact endpoint, with no test coverage catching the regression risk.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("Agent installer endpoints reject unauthenticated requests")
class ExecutorApiUnauthenticatedAccessTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("GET agent executable without any Authorization header should be unauthorized")
  void givenNoAuthentication_shouldRejectExecutableDownload() throws Exception {
    mvc.perform(
            get("%s/executable/openaev/%s/%s"
                    .formatted(
                        AGENT_URI,
                        Endpoint.PLATFORM_TYPE.Linux.name(),
                        Endpoint.PLATFORM_ARCH.x86_64.name()))
                .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET agent package without any Authorization header should be unauthorized")
  void givenNoAuthentication_shouldRejectPackageDownload() throws Exception {
    mvc.perform(
            get("%s/package/openaev/%s/%s/%s"
                    .formatted(
                        AGENT_URI,
                        Endpoint.PLATFORM_TYPE.Windows.name(),
                        Endpoint.PLATFORM_ARCH.x86_64.name(),
                        EndpointService.SERVICE))
                .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "GET agent installer command without any Authorization header should be unauthorized")
  void givenNoAuthentication_shouldRejectInstallerCommand() throws Exception {
    mvc.perform(
            get("%s/installer/openaev/%s/%s"
                    .formatted(
                        AGENT_URI, Endpoint.PLATFORM_TYPE.Linux.name(), EndpointService.SERVICE))
                .accept(MediaType.TEXT_PLAIN_VALUE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "GET agent installer token without any Authorization header should be unauthorized")
  void givenNoAuthentication_shouldRejectInstallerToken() throws Exception {
    mvc.perform(get(AGENT_URI + "/installer/openaev/token").accept(MediaType.TEXT_PLAIN_VALUE))
        .andExpect(status().isUnauthorized());
  }
}
