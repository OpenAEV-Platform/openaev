package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.context.TenantContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

@DisplayName("DebugTenantSource (compatible with both tenant mechanisms)")
class DebugTenantSourceTest {

  private final DebugTenantSource source = new DebugTenantSource();

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("uses the v2 path selector when present")
  void v2PathSelector() {
    TenantContext.setCurrentTenant("v1-tenant"); // present, but the v2 selector wins
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tenants/abc/mappers");
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("tenantId", "tenant-from-path"));

    assertThat(source.currentTenant(request)).isEqualTo("tenant-from-path");
  }

  @Test
  @DisplayName("uses the v2 X-Tenant-Ids header when there is no path selector")
  void v2HeaderSelector() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mappers");
    request.addHeader("X-Tenant-Ids", "tenant-from-header");

    assertThat(source.currentTenant(request)).isEqualTo("tenant-from-header");
  }

  @Test
  @DisplayName("sanitises the caller-controlled selector (no log injection)")
  void sanitisesSelector() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mappers");
    request.addHeader("X-Tenant-Ids", "abc\r\n2026 FAKE LOG LINE injected");

    String tenant = source.currentTenant(request);

    assertThat(tenant).doesNotContain("\n").doesNotContain("\r").doesNotContain(" ");
    assertThat(tenant).isEqualTo("abc2026FAKELOGLINEinjected");
  }

  @Test
  @DisplayName("a selector with no tenant-id characters falls back to the v1 context")
  void blankAfterSanitisationFallsBack() {
    TenantContext.setCurrentTenant("v1-tenant");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mappers");
    request.addHeader("X-Tenant-Ids", "\n\t  ");

    assertThat(source.currentTenant(request)).isEqualTo("v1-tenant");
  }

  @Test
  @DisplayName("falls back to the v1 tenant context when no v2 selector is present")
  void v1Fallback() {
    TenantContext.setCurrentTenant("v1-tenant");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tags");

    assertThat(source.currentTenant(request)).isEqualTo("v1-tenant");
  }

  @Test
  @DisplayName("falls back to the default tenant when nothing is set")
  void defaultWhenNothing() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tags");

    assertThat(source.currentTenant(request))
        .isNotBlank()
        .isEqualTo(TenantContext.getCurrentTenant());
  }
}
