package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockConfig.WithMockXtmHubConfig;
import io.openaev.xtmhub.config.XtmHubConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("XtmHubConfig tests")
public class XtmHubConfigTest extends IntegrationTest {

  @Nested
  @WithMockXtmHubConfig(enable = true, url = "https://hub.filigran.io")
  @DisplayName("When XTM Hub is enabled with URL")
  public class withEnabledXTMHub {

    @Autowired private XtmHubConfig xtmHubConfig;

    @Test
    @DisplayName("returns enabled status and URL")
    public void shouldReturnEnabledStatusAndUrl() {
      assertThat(xtmHubConfig.getEnable()).isTrue();
      assertThat(xtmHubConfig.getUrl()).isEqualTo("https://hub.filigran.io");
      assertThat(xtmHubConfig.getApiUrl()).isEqualTo("https://hub.filigran.io");
    }
  }

  @Nested
  @WithMockXtmHubConfig(url = "https://hub.filigran.io", override_api_url = "http://localhost:4002")
  @DisplayName("When XTM Hub API URL is overridden")
  public class withOverrideApiUrl {

    @Autowired private XtmHubConfig xtmHubConfig;

    @Test
    @DisplayName("returns overridden API URL")
    public void shouldReturnOverriddenApiUrl() {
      assertThat(xtmHubConfig.getApiUrl()).isEqualTo("http://localhost:4002");
    }
  }

  @Nested
  @DisplayName("When XTM Hub is disabled")
  public class withDisabledXTMHub {

    @Autowired private XtmHubConfig xtmHubConfig;

    @Test
    @DisplayName("returns disabled status")
    public void shouldReturnDisabledStatus() {
      assertThat(xtmHubConfig.getEnable()).isFalse();
    }
  }
}
