package io.openbas.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.openbas.IntegrationTest;
import io.openbas.injectors.xtmhub.config.XTMHubConfig;
import io.openbas.utils.mockConfig.WithMockXTMHubConfig;
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
