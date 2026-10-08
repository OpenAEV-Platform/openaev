package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

  @Test
  void classifies_the_trusted_origin_separately_from_a_public_host() {
    assertEquals(
        ReportRenderEgressGuard.Verdict.TRUSTED,
        customOriginGuard.classify("https://render.internal:9443/reporting/abc/render"));
    assertEquals(
        ReportRenderEgressGuard.Verdict.PUBLIC,
        customOriginGuard.classify("https://8.8.8.8/logo.png"));
    assertEquals(
        ReportRenderEgressGuard.Verdict.BLOCKED,
        customOriginGuard.classify("http://127.0.0.1/hook"));
  }

  @Test
  void resumes_the_trusted_origin_with_the_render_token_untouched() {
    Route route = mock(Route.class);
    Request request = mock(Request.class);
    when(route.request()).thenReturn(request);
    when(request.url()).thenReturn("https://render.internal:9443/reporting/abc/render");

    customOriginGuard.handle(route);

    verify(route).resume();
    verify(route, never()).resume(any(Route.ResumeOptions.class));
  }

  @Test
  void strips_the_authorization_header_before_a_public_host_ever_sees_it() {
    Route route = mock(Route.class);
    Request request = mock(Request.class);
    when(route.request()).thenReturn(request);
    when(request.url()).thenReturn("https://8.8.8.8/logo.png");
    when(request.headers())
        .thenReturn(Map.of("authorization", "Bearer secret-render-token", "accept", "image/png"));

    customOriginGuard.handle(route);

    ArgumentCaptor<Route.ResumeOptions> captor = ArgumentCaptor.forClass(Route.ResumeOptions.class);
    verify(route).resume(captor.capture());
    Map<String, String> forwardedHeaders = captor.getValue().headers;
    assertFalse(forwardedHeaders.containsKey("authorization"));
    assertTrue(forwardedHeaders.containsKey("accept"));
  }

  @Test
  void does_not_forward_the_authorization_header_to_a_non_trusted_public_host() {
    Route route = mock(Route.class);
    Request request = mock(Request.class);
    when(route.request()).thenReturn(request);
    when(request.url()).thenReturn("https://8.8.8.8/logo.png");
    // Mixed-case header name: Playwright normally lower-cases it, but the strip must not rely on
    // that - it has to catch the render token under any casing.
    when(request.headers()).thenReturn(Map.of("Authorization", "Bearer secret-render-token"));

    customOriginGuard.handle(route);

    ArgumentCaptor<Route.ResumeOptions> captor = ArgumentCaptor.forClass(Route.ResumeOptions.class);
    verify(route).resume(captor.capture());
    boolean hasAuthorizationHeader =
        captor.getValue().headers.keySet().stream().anyMatch("authorization"::equalsIgnoreCase);
    assertFalse(hasAuthorizationHeader);
  }

  @Test
  void aborts_a_blocked_target_without_sending_any_request() {
    Route route = mock(Route.class);
    Request request = mock(Request.class);
    when(route.request()).thenReturn(request);
    when(request.url()).thenReturn("http://127.0.0.1/hook");

    customOriginGuard.handle(route);

    verify(route).abort("blockedbyclient");
    verify(route, never()).resume();
    verify(route, never()).resume(any(Route.ResumeOptions.class));
  }
}
