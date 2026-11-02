package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Report render egress guard (SSRF guard at the real fetch boundary)")
class ReportRenderEgressGuardTest {

  private static final String RENDER_TOKEN = "Bearer secret-render-token";

  private final ExternalResourceFetcher fetcher = mock(ExternalResourceFetcher.class);

  // Loopback default: no custom render-base-url, plain HTTP on port 8080.
  private final ReportRenderEgressGuard loopbackGuard =
      new ReportRenderEgressGuard("", 8080, false, fetcher);

  // Custom render-base-url: on-premise deployments pointing the renderer at another origin.
  private final ReportRenderEgressGuard customOriginGuard =
      new ReportRenderEgressGuard("https://render.internal:9443/app", 8080, false, fetcher);

  private static Route routeTo(final String url, final String method, final Request request) {
    Route route = mock(Route.class);
    when(route.request()).thenReturn(request);
    when(request.url()).thenReturn(url);
    when(request.method()).thenReturn(method);
    return route;
  }

  private static Route routeTo(final String url) {
    return routeTo(url, "GET", mock(Request.class));
  }

  @Test
  void classifies_the_trusted_loopback_origin_as_trusted() {
    assertEquals(
        ReportRenderEgressGuard.Verdict.TRUSTED,
        loopbackGuard.classify("http://localhost:8080/reporting/abc/render?format=pdf"));
    assertEquals(
        ReportRenderEgressGuard.Verdict.TRUSTED,
        loopbackGuard.classify("http://localhost:8080/api/reportings/abc"));
  }

  @Test
  void does_not_trust_loopback_on_a_different_port_than_the_trusted_origin() {
    assertEquals(
        ReportRenderEgressGuard.Verdict.EXTERNAL,
        loopbackGuard.classify("http://localhost:9999/x"));
  }

  @Test
  void does_not_trust_loopback_on_a_different_scheme_than_the_trusted_origin() {
    assertEquals(
        ReportRenderEgressGuard.Verdict.EXTERNAL,
        loopbackGuard.classify("https://localhost:8080/x"));
  }

  @Test
  void classifies_the_trusted_custom_origin_as_trusted() {
    assertEquals(
        ReportRenderEgressGuard.Verdict.TRUSTED,
        customOriginGuard.classify("https://render.internal:9443/reporting/abc/render"));
  }

  @Test
  void classifies_anything_else_as_external_whatever_it_points_to() {
    // Whether the target is public or internal is not decided here: the external fetch resolves
    // the host once and refuses internal addresses itself.
    assertEquals(
        ReportRenderEgressGuard.Verdict.EXTERNAL,
        customOriginGuard.classify("https://8.8.8.8/logo.png"));
    assertEquals(
        ReportRenderEgressGuard.Verdict.EXTERNAL,
        customOriginGuard.classify("http://127.0.0.1/hook"));
    assertEquals(
        ReportRenderEgressGuard.Verdict.EXTERNAL,
        customOriginGuard.classify("https://logo.example.com/logo.png"));
  }

  @Test
  void does_not_apply_network_rules_to_non_network_schemes() {
    assertEquals(
        ReportRenderEgressGuard.Verdict.TRUSTED,
        customOriginGuard.classify("data:image/png;base64,aGVsbG8="));
    assertEquals(
        ReportRenderEgressGuard.Verdict.TRUSTED, customOriginGuard.classify("about:blank"));
  }

  @Test
  void blocks_a_malformed_url_or_one_without_a_host() {
    assertEquals(ReportRenderEgressGuard.Verdict.BLOCKED, customOriginGuard.classify("not a url"));
    assertEquals(ReportRenderEgressGuard.Verdict.BLOCKED, customOriginGuard.classify("http:///x"));
  }

  @Test
  void attaches_the_render_token_to_a_request_to_the_trusted_origin() {
    Request request = mock(Request.class);
    Route route = routeTo("https://render.internal:9443/reporting/abc/render", "GET", request);
    when(request.headers()).thenReturn(Map.of("accept", "text/html"));

    customOriginGuard.handle(route, RENDER_TOKEN);

    ArgumentCaptor<Route.ResumeOptions> captor = ArgumentCaptor.forClass(Route.ResumeOptions.class);
    verify(route).resume(captor.capture());
    Map<String, String> forwardedHeaders = captor.getValue().headers;
    assertEquals(RENDER_TOKEN, forwardedHeaders.get("authorization"));
    assertEquals("text/html", forwardedHeaders.get("accept"));
    verifyNoInteractions(fetcher);
  }

  @Test
  void replaces_an_authorization_header_the_page_set_itself_on_the_trusted_origin() {
    Request request = mock(Request.class);
    Route route = routeTo("https://render.internal:9443/api/reportings/abc", "GET", request);
    when(request.headers()).thenReturn(Map.of("Authorization", "Bearer something-else"));

    customOriginGuard.handle(route, RENDER_TOKEN);

    ArgumentCaptor<Route.ResumeOptions> captor = ArgumentCaptor.forClass(Route.ResumeOptions.class);
    verify(route).resume(captor.capture());
    Map<String, String> forwardedHeaders = captor.getValue().headers;
    assertEquals(1, forwardedHeaders.size());
    assertEquals(RENDER_TOKEN, forwardedHeaders.get("authorization"));
  }

  @Test
  void resumes_the_trusted_origin_untouched_when_there_is_no_token_to_attach() {
    Route route = routeTo("https://render.internal:9443/reporting/abc/render");

    customOriginGuard.handle(route, null);

    verify(route).resume();
    verify(route, never()).resume(any(Route.ResumeOptions.class));
  }

  @Test
  void hands_an_external_resource_back_to_the_browser_without_letting_it_connect()
      throws Exception {
    Request request = mock(Request.class);
    Route route = routeTo("https://logo.example.com/logo.png", "GET", request);
    when(request.headers()).thenReturn(Map.of("accept", "image/png"));
    byte[] body = "png-bytes".getBytes(StandardCharsets.UTF_8);
    when(fetcher.fetch("GET", "https://logo.example.com/logo.png", Map.of("accept", "image/png")))
        .thenReturn(
            new ExternalResourceFetcher.FetchedResource(
                200, Map.of("content-type", "image/png"), body));

    customOriginGuard.handle(route, RENDER_TOKEN);

    ArgumentCaptor<Route.FulfillOptions> captor =
        ArgumentCaptor.forClass(Route.FulfillOptions.class);
    verify(route).fulfill(captor.capture());
    assertEquals(200, captor.getValue().status);
    assertEquals(Map.of("content-type", "image/png"), captor.getValue().headers);
    assertArrayEquals(body, captor.getValue().bodyBytes);
    verify(route, never()).resume();
    verify(route, never()).resume(any(Route.ResumeOptions.class));
  }

  @Test
  void aborts_an_external_request_that_is_not_a_read() {
    Route route = routeTo("https://logo.example.com/collect", "POST", mock(Request.class));

    customOriginGuard.handle(route, RENDER_TOKEN);

    verify(route).abort("blockedbyclient");
    verify(route, never()).fulfill(any(Route.FulfillOptions.class));
    verifyNoInteractions(fetcher);
  }

  @Test
  void aborts_an_external_request_whose_host_the_fetch_refused() throws Exception {
    Route route = routeTo("http://10.0.0.5/hook");
    when(fetcher.fetch(anyString(), anyString(), anyMap()))
        .thenThrow(new UnknownHostException("resolves to a private or internal address"));

    customOriginGuard.handle(route, RENDER_TOKEN);

    verify(route).abort("blockedbyclient");
    verify(route, never()).fulfill(any(Route.FulfillOptions.class));
  }

  @Test
  void aborts_an_external_request_that_could_not_be_fetched() throws Exception {
    Route route = routeTo("https://logo.example.com/logo.png");
    when(fetcher.fetch(anyString(), anyString(), anyMap())).thenThrow(new IOException("timeout"));

    customOriginGuard.handle(route, RENDER_TOKEN);

    verify(route).abort("failed");
    verify(route, never()).fulfill(any(Route.FulfillOptions.class));
  }

  @Test
  void aborts_a_blocked_target_without_sending_any_request() {
    Route route = routeTo("not a url");

    customOriginGuard.handle(route, RENDER_TOKEN);

    verify(route).abort("blockedbyclient");
    verify(route, never()).resume();
    verify(route, never()).resume(any(Route.ResumeOptions.class));
    verify(route, never()).fulfill(any(Route.FulfillOptions.class));
    verifyNoInteractions(fetcher);
  }
}
