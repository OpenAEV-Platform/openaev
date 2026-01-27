package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.*;
import io.openaev.utils.mapper.EndpointMapper;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;

public class EndpointServiceTest extends IntegrationTest {

  @Mock private EndpointMapper endpointMapper;
  @Mock private EndpointRepository endpointRepository;
  @Mock private ExecutorRepository executorRepository;
  @Mock private AssetGroupRepository assetGroupRepository;
  @Mock private AssetAgentJobRepository assetAgentJobRepository;
  @Mock private TagRepository tagRepository;
  @Mock private AgentService agentService;
  @Mock private AssetService assetService;

  private EndpointService endpointService;

  @BeforeEach
  void beforeEach() {
    this.endpointService =
        new EndpointService(
            endpointMapper,
            endpointRepository,
            executorRepository,
            assetGroupRepository,
            assetAgentJobRepository,
            tagRepository,
            agentService,
            assetService);
  }

  @Test
  @DisplayName("Test to get the JFrog URL")
  void testGetJFrogUrl() throws MalformedURLException {
    URL url = endpointService.getJFrogUrl("/path/to/", "jfrog.agent");

    assertNotNull(url);
    assertEquals("https://filigran.jfrog.io/artifactory/path/to/jfrog.agent", url.toString());
  }

  @ParameterizedTest
  @CsvSource({
    "'', '..'",
    "'/not/a/../path/to/', 'jfrog.agent'",
    "'/not/a/../../path/to/', 'jfrog.agent'",
    "'/not/a/path/to/', '..'",
    "'/not/a/path/to/', 'jfrog..agent'",
    "'/not/a/..%2F../path/to/', 'jfrog.agent'",
    "'/not/a/path/to/', 'jfrog..agent'",
    "'/not/a/../../path/to/', 'jfrog.agent'"
  })
  void shouldThrowSecurityExceptionForTraversalPathAttempt(String path, String filename) {
    assertThrows(SecurityException.class, () -> endpointService.getJFrogUrl(path, filename));
  }
}
