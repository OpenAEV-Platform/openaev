package io.openbas.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.openbas.injectors.opencti.config.OpenCTIConfig;
import io.openbas.utils.mockConfig.WithMockOpenCTIConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("OpenCTIConfig tests")
public class OpenCTIConfigTest {
  @Nested
  @WithMockOpenCTIConfig(url = "public_url")
  @DisplayName("When setting only the public URL")
  public class withOnlyUrlNotApiUrl {
    @Autowired private OpenCTIConfig openCTIConfig;

    @Test
    @DisplayName("returns a variant of the public URL for the API URL")
    public void shouldReturnVariantOfPublicUrlForApiUrl() {
      assertThat(openCTIConfig.getApiUrl()).isEqualTo("public_url/graphql");
      assertThat(openCTIConfig.getUrl()).isEqualTo("public_url");
    }
  }

  @Nested
  @WithMockOpenCTIConfig(apiUrl = "api_url", url = "public_url")
  @DisplayName("When setting both URL and API URL")
  public class withSetApiUrlAndUrl {
    @Autowired private OpenCTIConfig openCTIConfig;

    @Test
    @DisplayName("returns different URLs for API URL and URL")
    public void shouldReturnDifferentValuesForPublicAndApiUrl() {
      assertThat(openCTIConfig.getApiUrl()).isEqualTo("api_url");
      assertThat(openCTIConfig.getUrl()).isEqualTo("public_url");
    }
  }
}
