package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.openaev.security.ssrf.PublicOnlyDnsResolver;
import io.openaev.security.ssrf.TimeoutHostResolver;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("External resource fetcher (resolves once, public addresses only)")
class ExternalResourceFetcherTest {

  private static final int MAX_BODY_BYTES = 1024;

  private HttpServer server;
  private final List<Map<String, List<String>>> receivedHeaders =
      Collections.synchronizedList(new ArrayList<>());
  private LoopbackAsPublicDnsResolver resolver;
  private ExternalResourceFetcher fetcher;

  @BeforeEach
  void setUp() throws IOException {
    this.server = HttpServer.create(new InetSocketAddress(0), 0);
    this.server.createContext(
        "/logo.png",
        exchange -> {
          this.receivedHeaders.add(Map.copyOf(exchange.getRequestHeaders()));
          exchange.getResponseHeaders().add("Content-Type", "image/png");
          exchange.getResponseHeaders().add("Set-Cookie", "tracking=1");
          byte[] body = "png-bytes".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    this.server.createContext(
        "/redirect-to-public",
        exchange -> {
          // Another name: a same-host redirect would reuse the pooled, already validated
          // connection.
          exchange.getResponseHeaders().add("Location", url("other-public.test", "/logo.png"));
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    this.server.createContext(
        "/redirect-to-internal",
        exchange -> {
          exchange.getResponseHeaders().add("Location", url("127.0.0.1", "/logo.png"));
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    this.server.createContext(
        "/redirect-loop",
        exchange -> {
          exchange.getResponseHeaders().add("Location", url("public.test", "/redirect-loop"));
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    this.server.createContext(
        "/huge",
        exchange -> {
          byte[] body = new byte[MAX_BODY_BYTES + 1];
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    this.server.start();
    this.resolver = new LoopbackAsPublicDnsResolver("public.test", "other-public.test");
    this.fetcher = new ExternalResourceFetcher(this.resolver, MAX_BODY_BYTES);
  }

  @AfterEach
  void tearDown() throws IOException {
    this.fetcher.close();
    this.server.stop(0);
  }

  private String url(final String host, final String path) {
    return "http://" + host + ":" + this.server.getAddress().getPort() + path;
  }

  @Test
  void fetches_a_resource_from_a_host_resolving_to_a_public_address() throws Exception {
    ExternalResourceFetcher.FetchedResource resource =
        this.fetcher.fetch("GET", url("public.test", "/logo.png"), Map.of());

    assertEquals(200, resource.status());
    assertEquals("image/png", resource.headers().get("content-type"));
    assertArrayEquals("png-bytes".getBytes(StandardCharsets.UTF_8), resource.body());
  }

  @Test
  void resolves_the_host_exactly_once_and_connects_to_what_it_validated() throws Exception {
    // A second lookup (the browser's own, in the previous design) is what DNS rebinding abuses.
    this.fetcher.fetch("GET", url("public.test", "/logo.png"), Map.of());

    assertEquals(1, this.resolver.lookups());
  }

  @Test
  void refuses_an_internal_target_without_connecting_to_it() {
    assertThrows(
        UnknownHostException.class,
        () -> this.fetcher.fetch("GET", url("127.0.0.1", "/logo.png"), Map.of()));
    assertThrows(
        UnknownHostException.class,
        () -> this.fetcher.fetch("GET", url("localhost", "/logo.png"), Map.of()));

    assertTrue(this.receivedHeaders.isEmpty());
  }

  @Test
  void follows_a_redirect_itself_and_validates_the_new_hop() throws Exception {
    ExternalResourceFetcher.FetchedResource resource =
        this.fetcher.fetch("GET", url("public.test", "/redirect-to-public"), Map.of());

    assertEquals(200, resource.status());
    assertArrayEquals("png-bytes".getBytes(StandardCharsets.UTF_8), resource.body());
    assertEquals(2, this.resolver.lookups(), "one resolution per hop, each one validated");
  }

  @Test
  void refuses_a_redirect_to_an_internal_target_without_connecting_to_it() {
    // A redirect handed back to the browser would be followed by it, unchecked: the redirect has
    // to be followed here, where the new hop goes through the resolver like any other request.
    assertThrows(
        UnknownHostException.class,
        () -> this.fetcher.fetch("GET", url("public.test", "/redirect-to-internal"), Map.of()));

    assertTrue(this.receivedHeaders.isEmpty(), "the internal hop was never connected to");
  }

  @Test
  void never_hands_a_location_header_back_to_the_browser() throws Exception {
    // Even if one slips through (a redirect status the client does not follow), the browser must
    // have no target to go to by itself.
    this.server.createContext(
        "/created",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "http://127.0.0.1:1/internal");
          exchange.sendResponseHeaders(201, -1);
          exchange.close();
        });

    ExternalResourceFetcher.FetchedResource resource =
        this.fetcher.fetch("GET", url("public.test", "/created"), Map.of());

    assertEquals(201, resource.status());
    assertNull(resource.headers().get("location"));
  }

  @Test
  void gives_up_on_a_redirect_loop() {
    assertThrows(
        IOException.class,
        () -> this.fetcher.fetch("GET", url("public.test", "/redirect-loop"), Map.of()));
  }

  @Test
  void never_forwards_credentials_but_keeps_the_headers_a_resource_fetch_needs() throws Exception {
    this.fetcher.fetch(
        "GET",
        url("public.test", "/logo.png"),
        Map.of(
            "authorization", "Bearer secret-render-token",
            "cookie", "session=abc",
            "accept", "image/png",
            "user-agent", "HeadlessChrome"));

    Map<String, List<String>> received = this.receivedHeaders.get(0);
    assertFalse(received.containsKey("Authorization"));
    assertFalse(received.containsKey("Cookie"));
    assertEquals(List.of("image/png"), received.get("Accept"));
    assertEquals(List.of("HeadlessChrome"), received.get("User-agent"));
  }

  @Test
  void never_hands_back_cookies_set_by_the_external_host() throws Exception {
    ExternalResourceFetcher.FetchedResource resource =
        this.fetcher.fetch("GET", url("public.test", "/logo.png"), Map.of());

    assertNull(resource.headers().get("set-cookie"));
  }

  @Test
  void refuses_a_resource_larger_than_the_limit() {
    assertThrows(
        IOException.class, () -> this.fetcher.fetch("GET", url("public.test", "/huge"), Map.of()));
  }

  @Test
  void refuses_an_internal_target_by_default() throws Exception {
    // The production wiring, with openaev.theme.asset-allow-internal-targets left off.
    ExternalResourceFetcher defaultFetcher = new ExternalResourceFetcher(false);
    try {
      assertThrows(
          UnknownHostException.class,
          () -> defaultFetcher.fetch("GET", url("127.0.0.1", "/logo.png"), Map.of()));

      assertTrue(this.receivedHeaders.isEmpty());
    } finally {
      defaultFetcher.close();
    }
  }

  @Test
  void fetches_an_internal_target_once_the_operator_opted_in() throws Exception {
    // The production wiring, with openaev.theme.asset-allow-internal-targets turned on: an
    // on-premise network whose logo lives on an internal server.
    ExternalResourceFetcher onPremiseFetcher = new ExternalResourceFetcher(true);
    try {
      ExternalResourceFetcher.FetchedResource resource =
          onPremiseFetcher.fetch("GET", url("127.0.0.1", "/logo.png"), Map.of());

      assertEquals(200, resource.status());
      assertArrayEquals("png-bytes".getBytes(StandardCharsets.UTF_8), resource.body());
    } finally {
      onPremiseFetcher.close();
    }
  }

  @Test
  void still_never_forwards_credentials_once_the_operator_opted_in() throws Exception {
    // The opt-in only lifts the address check: credentials still never leave.
    ExternalResourceFetcher onPremiseFetcher = new ExternalResourceFetcher(true);
    try {
      onPremiseFetcher.fetch(
          "GET", url("127.0.0.1", "/logo.png"), Map.of("authorization", "Bearer secret"));

      assertFalse(this.receivedHeaders.get(0).containsKey("Authorization"));
    } finally {
      onPremiseFetcher.close();
    }
  }

  @Test
  void fails_fast_when_dns_never_answers_instead_of_holding_the_caller() throws Exception {
    // The fetch runs on the browser driver's dispatch thread: a DNS server that never answers must
    // fail the request after the lookup timeout, not after the operating system's own patience.
    CountDownLatch dnsNeverAnswers = new CountDownLatch(1);
    ExternalResourceFetcher stuckDnsFetcher =
        new ExternalResourceFetcher(
            new PublicOnlyDnsResolver(
                new TimeoutHostResolver(
                    host -> {
                      try {
                        dnsNeverAnswers.await();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return new InetAddress[0];
                    },
                    Duration.ofMillis(200),
                    2)),
            MAX_BODY_BYTES);
    try {
      long start = System.nanoTime();
      assertThrows(
          UnknownHostException.class,
          () -> stuckDnsFetcher.fetch("GET", "http://slow-dns.example.com/logo.png", Map.of()));
      long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

      assertTrue(elapsedMillis < 2_000, "released after the lookup timeout: " + elapsedMillis);
    } finally {
      dnsNeverAnswers.countDown();
      stuckDnsFetcher.close();
    }
  }
}
