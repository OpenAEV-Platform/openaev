package io.openaev.rest.payload.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.IocValidationTestKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IOC validation benign command content")
class IocValidationCommandContentTest {

  @Test
  @DisplayName("the HTTP HEAD test always goes through the egress proxy, whatever NO_PROXY says")
  void given_httpHeadOnUnix_should_forceTheProxy() {
    String content =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.HTTP_HEAD, false);
    assertThat(content).contains("--noproxy ''").contains("--proxy ");
    assertThat(content.indexOf("--noproxy ''")).isLessThan(content.indexOf("--proxy "));
  }

  @Test
  @DisplayName("the Windows HTTP HEAD test passes the egress proxy explicitly")
  void given_httpHeadOnWindows_should_passTheProxy() {
    String content =
        PayloadService.iocValidationCommandContent(IocValidationTestKind.HTTP_HEAD, true);
    assertThat(content).contains("-Proxy ");
  }
}
