package io.openbas.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.openbas.IntegrationTest;
import io.openbas.utils.mockConfig.WithMockXTMHubConfig;
import io.openbas.xtmhub.config.XTMHubConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("XTMHubConfig tests")
public class XTMHubConfigTest extends IntegrationTest {

  @Nested
  @WithMockXTMHubConfig(enable = true, url = "https://hub.filigran.io")
  @DisplayName("When XTM Hub is enabled with URL")
  public class withEnabledXTMHub {

    @Autowired private XTMHubConfig xtmHubConfig;

    @Test
    @DisplayName("returns enabled status and URL")
    public void shouldReturnEnabledStatusAndUrl() {
      assertThat(xtmHubConfig.getEnable()).isTrue();
      assertThat(xtmHubConfig.getUrl()).isEqualTo("https://hub.filigran.io");
      assertThat(xtmHubConfig.getApiUrl()).isEqualTo("https://hub.filigran.io");
    }
  }

  @Nested
  @WithMockXTMHubConfig(url = "https://hub.filigran.io", override_api_url = "http://localhost:4002")
  @DisplayName("When XTM Hub API URL is overridden")
  public class withOverrideApiUrl {

    @Autowired private XTMHubConfig xtmHubConfig;

    @Test
    @DisplayName("returns overridden API URL")
    public void shouldReturnOverriddenApiUrl() {
      assertThat(xtmHubConfig.getApiUrl()).isEqualTo("http://localhost:4002");
    }
  }

  @Nested
  @WithMockXTMHubConfig(enable = false)
  @DisplayName("When XTM Hub is disabled")
  public class withDisabledXTMHub {

    @Autowired private XTMHubConfig xtmHubConfig;

    @Test
    @DisplayName("returns disabled status")
    public void shouldReturnDisabledStatus() {
      assertThat(xtmHubConfig.getEnable()).isFalse();
    }
  }
}
