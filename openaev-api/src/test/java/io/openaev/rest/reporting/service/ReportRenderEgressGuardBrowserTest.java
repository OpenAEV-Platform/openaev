package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs {@link ReportRenderEgressGuard} against a real headless Chromium, because the guard's whole
 * point is what actually goes out on the wire: a mocked {@code Route} cannot tell whether the
 * render token really stays off the requests to a tenant-controlled host, nor whether the browser
 * still connects to anything by itself.
 *
 * <p>Three local servers stand for the three kinds of target. The trusted origin is {@code
 * localhost:<port>}. The "public" host is {@code public.test}: it resolves to nothing for Chromium
 * (which must therefore never try to reach it itself) and, for the guard's fetch, to the public
 * server. The internal target is a loopback literal that must never be contacted.
 */
@DisplayName("Report render egress guard on a real Chromium (what really goes out on the wire)")
class ReportRenderEgressGuardBrowserTest {

  private static final String RENDER_TOKEN = "Bearer secret-render-token";

  private RecordingServer trusted;
  private RecordingServer publicHost;
  private RecordingServer internal;
  private ExternalResourceFetcher fetcher;
  private ReportRenderEgressGuard guard;

  // Launching Chromium is the slow part: once for the class, a fresh context for each test.
  private static Playwright playwright;
  private static Browser browser;

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
    browser =
        playwright
            .chromium()
            .launch(
                // Only the trusted origin of the "public render url" scenario is resolvable by
                // Chromium: every other name (public.test...) must stay unreachable for it.
                new BrowserType.LaunchOptions()
                    .setArgs(List.of("--host-resolver-rules=MAP trusted.test 127.0.0.1")));
  }

  @AfterAll
  static void closeBrowser() {
    browser.close();
    playwright.close();
  }

  @BeforeEach
  void setUp() throws IOException {
    this.internal = new RecordingServer();
    this.trusted = new RecordingServer();
    this.publicHost = new RecordingServer();
    this.publicHost.on(
        "/redirect-to-internal",
        redirectTo("http://127.0.0.1:" + this.internal.port() + "/internal.png"));
    this.publicHost.on(
        "/redirect-to-trusted",
        redirectTo("http://localhost:" + this.trusted.port() + "/api/from-redirect"));
    this.trusted.on(
        "/render",
        page(
            "<html><body>"
                + "<img src='http://public.test:"
                + this.publicHost.port()
                + "/logo.png'>"
                + "<img src='http://public.test:"
                + this.publicHost.port()
                + "/redirect-to-internal'>"
                + "<img src='http://public.test:"
                + this.publicHost.port()
                + "/redirect-to-trusted'>"
                + "<img src='http://127.0.0.1:"
                + this.internal.port()
                + "/internal.png'>"
                + "</body></html>"));

    this.fetcher =
        new ExternalResourceFetcher(new LoopbackAsPublicDnsResolver("public.test"), 1024);
    this.guard =
        new ReportRenderEgressGuard(
            "http://localhost:" + this.trusted.port(), 8080, false, this.fetcher);
  }

  @AfterEach
  void tearDown() throws IOException {
    this.fetcher.close();
    this.trusted.stop();
    this.publicHost.stop();
    this.internal.stop();
  }

  @Test
  void the_render_token_reaches_the_trusted_origin_and_nobody_else() {
    renderTrustedPage();

    List<String> trustedHeaders = this.trusted.authorizationHeaders();
    assertTrue(trustedHeaders.size() >= 2, "page load and same-origin API call");
    assertTrue(
        trustedHeaders.stream().allMatch(RENDER_TOKEN::equals),
        "every request to the trusted origin carries the render token");
    List<String> externalHeaders = this.publicHost.authorizationHeaders();
    assertFalse(externalHeaders.isEmpty(), "the external host is fetched");
    assertTrue(
        externalHeaders.stream().allMatch(Objects::isNull),
        "but never receives any Authorization header");
  }

  @Test
  void an_external_resource_is_fetched_for_the_browser_which_never_resolves_the_host_itself() {
    renderTrustedPage();

    // public.test resolves to nothing for Chromium: that the image request reached the public
    // server at all means the guard fetched it, not the browser.
    assertTrue(
        this.publicHost.paths().contains("/logo.png"),
        "the guard fetched the logo on the browser's behalf");
  }

  @Test
  void an_internal_target_is_never_contacted() {
    renderTrustedPage();

    assertTrue(
        this.internal.paths().isEmpty(),
        "neither a direct reference nor a redirect from a public host reaches the internal target,"
            + " but it received: "
            + this.internal.paths());
  }

  @Test
  void a_redirect_from_a_public_host_to_an_internal_target_is_refused() {
    renderTrustedPage();

    // The public host did answer with a redirect, which the guard's fetch followed itself and
    // refused at the internal hop. Handed back to Chromium instead, the redirect would have been
    // followed by the browser straight to the internal target, unchecked.
    assertTrue(this.publicHost.paths().contains("/redirect-to-internal"));
    assertTrue(
        this.internal.paths().isEmpty(),
        "the internal hop must never be contacted, but it received: " + this.internal.paths());
  }

  @Test
  void a_redirect_from_a_public_host_to_the_loopback_trusted_origin_never_arrives() {
    renderTrustedPage();

    // The redirect target is the platform's own origin, but it is reached by the guard's external
    // fetch, which refuses loopback: the hostile host cannot make the platform's API be called,
    // with or without the render token.
    assertTrue(this.publicHost.paths().contains("/redirect-to-trusted"));
    assertFalse(
        this.trusted.paths().contains("/api/from-redirect"),
        "the trusted origin must not be reached through a redirect from an external host");
  }

  @Test
  void a_redirect_from_a_public_host_to_a_publicly_resolvable_trusted_origin_carries_no_token()
      throws IOException {
    // render-base-url may point at a publicly resolvable name (a reverse proxy in front of the
    // platform). A hostile host redirecting there must not be able to borrow the render token: the
    // redirect is followed by the external fetch, which never carries credentials.
    RecordingServer publicTrusted = new RecordingServer();
    ExternalResourceFetcher publicFetcher =
        new ExternalResourceFetcher(
            new LoopbackAsPublicDnsResolver("public.test", "trusted.test"), 1024);
    try {
      this.publicHost.on(
          "/redirect-to-public-trusted",
          redirectTo("http://trusted.test:" + publicTrusted.port() + "/api/from-redirect"));
      publicTrusted.on(
          "/render",
          page(
              "<html><body><img src='http://public.test:"
                  + this.publicHost.port()
                  + "/redirect-to-public-trusted'></body></html>"));
      ReportRenderEgressGuard publicTrustedGuard =
          new ReportRenderEgressGuard(
              "http://trusted.test:" + publicTrusted.port(), 8080, false, publicFetcher);

      renderPage(publicTrustedGuard, "http://trusted.test:" + publicTrusted.port() + "/render");

      assertEquals(
          List.of(RENDER_TOKEN),
          publicTrusted.authorizationHeadersFor("/render"),
          "the page itself is requested by the browser with the render token");
      assertEquals(
          Collections.singletonList(null),
          publicTrusted.authorizationHeadersFor("/api/from-redirect"),
          "the redirected request reaches the platform, but as an anonymous external fetch");
    } finally {
      publicFetcher.close();
      publicTrusted.stop();
    }
  }

  private void renderTrustedPage() {
    renderPage(this.guard, "http://localhost:" + this.trusted.port() + "/render");
  }

  private void renderPage(final ReportRenderEgressGuard guardToInstall, final String url) {
    try (BrowserContext context = browser.newContext()) {
      guardToInstall.install(context, RENDER_TOKEN);
      Page page = context.newPage();
      page.navigate(url);
      page.waitForLoadState(LoadState.NETWORKIDLE);
      // A same-origin call, like the SPA's own API calls, must be authenticated too.
      page.evaluate("() => fetch('/api/ping').then(r => r.status)");
    }
  }

  private static HttpHandler page(final String html) {
    return exchange -> {
      byte[] body = html.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    };
  }

  private static HttpHandler redirectTo(final String location) {
    return exchange -> {
      exchange.getResponseHeaders().add("Location", location);
      exchange.sendResponseHeaders(302, -1);
    };
  }

  /** Tiny HTTP server that records the path and Authorization header of every request. */
  private static final class RecordingServer {
    private record Seen(String path, String authorization) {}

    private final HttpServer server;
    private final List<Seen> requests = Collections.synchronizedList(new ArrayList<>());

    RecordingServer() throws IOException {
      this.server = HttpServer.create(new InetSocketAddress(0), 0);
      // Anything not routed explicitly: an image (for the logo) or an empty answer (for the api).
      on(
          "/",
          exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, -1);
          });
      this.server.start();
    }

    void on(final String path, final HttpHandler handler) {
      this.server.createContext(
          path,
          exchange -> {
            this.requests.add(
                new Seen(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization")));
            handler.handle(exchange);
            exchange.close();
          });
    }

    int port() {
      return this.server.getAddress().getPort();
    }

    private List<Seen> snapshot() {
      synchronized (this.requests) {
        return new ArrayList<>(this.requests);
      }
    }

    List<String> paths() {
      return snapshot().stream().map(Seen::path).toList();
    }

    /** Authorization header of every request, null where a request carried none. */
    List<String> authorizationHeaders() {
      return snapshot().stream().map(Seen::authorization).toList();
    }

    /** Authorization header of every request to the given path, null where it carried none. */
    List<String> authorizationHeadersFor(final String path) {
      return snapshot().stream()
          .filter(seen -> seen.path().equals(path))
          .map(Seen::authorization)
          .toList();
    }

    void stop() {
      this.server.stop(0);
    }
  }
}
