package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.utils.mockConfig.WithMockOpenCTIConfig;
import io.openaev.utilstest.RabbitMQTestListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("OpenCTIConfig tests")
public class OpenCTIConfigTest extends IntegrationTest {

  @Nested
  @WithMockOpenCTIConfig(url = "public_url")
  @DisplayName("When setting only the public URL")
  class withOnlyUrlNotApiUrl {
    @Autowired private XtmConfig xtmConfig;

    @Test
    @DisplayName("returns a variant of the public URL for the API URL")
    void shouldReturnVariantOfPublicUrlForApiUrl() {
      assertThat(xtmConfig.getOpencti().get(TenantContext.getCurrentTenant()).getApiUrl())
          .isEqualTo("public_url/graphql");
      assertThat(xtmConfig.getOpencti().get(TenantContext.getCurrentTenant()).getUrl())
          .isEqualTo("public_url");
    }
  }

  @Nested
  @WithMockOpenCTIConfig(apiUrl = "api_url", url = "public_url")
  @DisplayName("When setting both URL and API URL")
  class withSetApiUrlAndUrl {
    @Autowired private XtmConfig openCTIConfig;

    @Test
    @DisplayName("returns different URLs for API URL and URL")
    void shouldReturnDifferentValuesForPublicAndApiUrl() {
      assertThat(openCTIConfig.getOpencti().get(TenantContext.getCurrentTenant()).getApiUrl())
          .isEqualTo("api_url");
      assertThat(openCTIConfig.getOpencti().get(TenantContext.getCurrentTenant()).getUrl())
          .isEqualTo("public_url");
    }
  }
}
