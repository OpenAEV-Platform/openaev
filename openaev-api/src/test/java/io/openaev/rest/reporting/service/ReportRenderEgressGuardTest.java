package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Report render egress guard (SSRF guard at the real fetch boundary)")
class ReportRenderEgressGuardTest {

  // Loopback default: no custom render-base-url, plain HTTP on port 8080.
  private final ReportRenderEgressGuard loopbackGuard =
      new ReportRenderEgressGuard("", 8080, false);

  // Custom render-base-url: on-premise deployments pointing the renderer at another origin.
  private final ReportRenderEgressGuard customOriginGuard =
      new ReportRenderEgressGuard("https://render.internal:9443/app", 8080, false);

  @Test
  void allows_the_trusted_loopback_origin() {
    assertTrue(loopbackGuard.isAllowed("http://localhost:8080/reporting/abc/render?format=pdf"));
    assertTrue(loopbackGuard.isAllowed("http://localhost:8080/api/reportings/abc"));
  }

  @Test
  void denies_loopback_on_a_different_port_than_the_trusted_origin() {
    assertFalse(loopbackGuard.isAllowed("http://localhost:9999/evil"));
  }

  @Test
  void denies_loopback_on_a_different_scheme_than_the_trusted_origin() {
    assertFalse(loopbackGuard.isAllowed("https://localhost:8080/evil"));
  }

  @Test
  void allows_the_trusted_custom_origin() {
    assertTrue(customOriginGuard.isAllowed("https://render.internal:9443/reporting/abc/render"));
  }

  @Test
  void denies_a_private_target_that_is_not_the_trusted_origin() {
    assertFalse(customOriginGuard.isAllowed("http://127.0.0.1/hook"));
    assertFalse(customOriginGuard.isAllowed("http://10.0.0.5/hook"));
    assertFalse(customOriginGuard.isAllowed("http://169.254.169.254/latest/meta-data"));
  }

  @Test
  void allows_a_public_target() {
    assertTrue(customOriginGuard.isAllowed("https://8.8.8.8/logo.png"));
  }

  @Test
  void denies_an_unresolvable_host() {
    // Unlike config-save-time validators, the render browser fails closed here: it has no
    // legitimate reason to reach anything beyond its own trusted origin and verified public hosts.
    assertFalse(customOriginGuard.isAllowed("https://no-such-host.invalid/logo.png"));
  }

  @Test
  void allows_non_network_schemes() {
    assertTrue(customOriginGuard.isAllowed("data:image/png;base64,aGVsbG8="));
    assertTrue(customOriginGuard.isAllowed("about:blank"));
  }

  @Test
  void denies_a_malformed_url() {
    assertFalse(customOriginGuard.isAllowed("not a url"));
  }
}
