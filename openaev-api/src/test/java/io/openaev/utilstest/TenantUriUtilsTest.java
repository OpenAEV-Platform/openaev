package io.openaev.utilstest;

import static io.openaev.config.TenantUriUtils.TENANT_BASE_PATH;
import static io.openaev.config.TenantUriUtils.TENANT_ID_PATH_VARIABLE;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.when;

import io.openaev.config.TenantUriUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(MockitoExtension.class)
public class TenantUriUtilsTest {
  private final TenantUriUtils tenantUriUtils = new TenantUriUtils();
  @Mock private HttpServletRequest mockRequest;

  static final String tenantId = "c078ce91-d4b8-4d29-a17e-b8fea925dc2c";

  static String generateTenantUri(String tenantId) {
    return generateTenantUri(tenantId, "");
  }

  static String generateTenantUri(String tenantId, String subpath) {
    return TENANT_BASE_PATH + tenantId + subpath;
  }

  @Nested
  @DisplayName("When there is no URI_TEMPLATE_VARIABLES_ATTRIBUTE")
  public class NoUriTemplateAttributes {
    static Stream<Arguments> uris() {
      return Stream.of(
          Arguments.of(generateTenantUri(tenantId), Optional.of(tenantId)),
          Arguments.of(null, Optional.empty()),
          Arguments.of(generateTenantUri(tenantId, "/more"), Optional.of(tenantId)),
          Arguments.of(generateTenantUri("subpath/" + tenantId, "/more"), Optional.empty()),
          Arguments.of("/generic/" + tenantId, Optional.empty()),
          Arguments.of("/generic/api/tenants/" + tenantId, Optional.empty()),
          // tenant segment must be a full UUID ending at a path-segment boundary
          Arguments.of(generateTenantUri(tenantId + "garbage", "/more"), Optional.empty()),
          Arguments.of(generateTenantUri("deadbeef", "/more"), Optional.empty()),
          Arguments.of(generateTenantUri("not-a-uuid"), Optional.empty()));
    }

    @ParameterizedTest(name = "given uri={0} should return {1}")
    @MethodSource("uris")
    void matchTest(String uri, Optional<String> outcome) {
      when(mockRequest.getRequestURI()).thenReturn(uri);
      assertThat(tenantUriUtils.getTenantIdFromRequestUrl(mockRequest)).isEqualTo(outcome);
    }

    static Stream<Arguments> contextPathUris() {
      return Stream.of(
          // context path is stripped before matching the anchored pattern
          Arguments.of(
              "/openaev" + generateTenantUri(tenantId, "/more"), "/openaev", Optional.of(tenantId)),
          Arguments.of("/openaev" + generateTenantUri(tenantId), "/openaev", Optional.of(tenantId)),
          // root context path (empty string) leaves the URI untouched
          Arguments.of(generateTenantUri(tenantId, "/more"), "", Optional.of(tenantId)),
          // URI not under the context path is matched as-is
          Arguments.of(generateTenantUri(tenantId), "/other", Optional.of(tenantId)),
          // a tenant-looking path nested under another prefix still does not match
          Arguments.of(
              "/openaev/generic" + generateTenantUri(tenantId), "/openaev", Optional.empty()));
    }

    @ParameterizedTest(name = "given uri={0} and contextPath={1} should return {2}")
    @MethodSource("contextPathUris")
    void contextPathMatchTest(String uri, String contextPath, Optional<String> outcome) {
      when(mockRequest.getRequestURI()).thenReturn(uri);
      when(mockRequest.getContextPath()).thenReturn(contextPath);
      assertThat(tenantUriUtils.getTenantIdFromRequestUrl(mockRequest)).isEqualTo(outcome);
    }
  }

  @Nested
  @DisplayName("When there is URI_TEMPLATE_VARIABLES_ATTRIBUTE")
  public class WithUriTemplateAttributes {
    @Test
    @DisplayName("given tenant part is found, then return tenantId")
    void given_tenantPartIsFound_then_returnTenantId() {
      when(mockRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
          .thenReturn(Map.of(TENANT_ID_PATH_VARIABLE, tenantId));
      assertThat(tenantUriUtils.getTenantIdFromRequestUrl(mockRequest))
          .isEqualTo(Optional.of(tenantId));
    }

    @Test
    @DisplayName("given tenant part is not found, then return empty")
    void given_tenantPartIsNotFound_then_returnEmpty() {
      when(mockRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
          .thenReturn(Map.of());
      when(mockRequest.getRequestURI()).thenReturn("");
      assertThat(tenantUriUtils.getTenantIdFromRequestUrl(mockRequest)).isEmpty();
    }
  }
}
