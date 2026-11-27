package io.openaev.rest.stream;

import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static io.openaev.rest.stream.StreamApi.STREAM_URI;
import static io.openaev.rest.stream.StreamApi.TENANT_STREAM_URI;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.service.StreamService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The tenant a stream subscriber receives the events of is the tenant of its route: the one the
 * {@code /api/tenants/{tenantId}/stream} route names, and the default tenant on {@code
 * /api/stream}, whatever its {@code X-Tenant-Ids} header names. Each subscriber is a real HTTP
 * client, and the subscribing thread and the delivering thread carry no ambient tenant.
 *
 * <p>Not {@code @Transactional}: the server threads read the committed tenants and memberships.
 * Every committed row is removed in {@code @AfterEach}.
 */
@WithMockUser(isAdmin = true)
@DisplayName("A stream subscriber receives the events of the tenant of its route")
class StreamApiTenantRouteTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  private static final int PINGS_AFTER_DELIVERY = 3;

  @Autowired private StreamService streamService;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private ObjectMapper mapper;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantMembershipCacheManager tenantMembershipCacheManager;

  @LocalServerPort private int port;

  @Value("${openaev.admin.token}")
  private String adminToken;

  private String tenantA;
  private String tenantB;
  private final List<Socket> sockets = new ArrayList<>();

  @BeforeEach
  void twoTenantsTheAdminBelongsTo() throws Exception {
    tenantA = tenantIsolationHelper.createTenant("stream-route-a").getId();
    tenantB = tenantIsolationHelper.createTenant("stream-route-b").getId();
    tenantIsolationHelper.attachUserToTenant(User.ADMIN_UUID, tenantA);
    tenantIsolationHelper.attachUserToTenant(User.ADMIN_UUID, tenantB);
  }

  @AfterEach
  void closeTheStreamsAndRemoveTheTenants() throws IOException {
    for (Socket socket : sockets) {
      socket.close();
    }
    for (String tenantId : List.of(tenantA, tenantB)) {
      jdbc.update(
          "DELETE FROM users_tenants WHERE user_id = ? AND tenant_id = ?",
          User.ADMIN_UUID,
          tenantId);
      tenantMembershipCacheManager.evict(User.ADMIN_UUID, tenantId);
    }
    tenantIsolationHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Subscribers of tenant A, of tenant B and of the route without a tenant")
  class ThreeSubscribers {

    @Test
    @DisplayName(
        "given an event of each tenant, should deliver each one to the subscriber of its tenant"
            + " only")
    void given_anEventOfEachTenant_should_deliverEachOneToTheSubscriberOfItsTenantOnly()
        throws Exception {
      // Arrange
      Subscriber pathRouteOfA =
          subscribe(TENANT_STREAM_URI.replace("{tenantId}", tenantA), null, tenantA);
      Subscriber pathRouteOfB =
          subscribe(TENANT_STREAM_URI.replace("{tenantId}", tenantB), null, tenantB);
      Subscriber headerRouteNamingB = subscribe(STREAM_URI, tenantB, Tenant.DEFAULT_TENANT_UUID);
      String tagOfA = UUID.randomUUID().toString();
      String tagOfB = UUID.randomUUID().toString();
      String tagOfDefault = UUID.randomUUID().toString();

      // Act: delivered on the stream executor, as after a commit
      streamService.listenDatabaseUpdate(eventOfTag(tagOfA, tenantA));
      streamService.listenDatabaseUpdate(eventOfTag(tagOfB, tenantB));
      streamService.listenDatabaseUpdate(eventOfTag(tagOfDefault, Tenant.DEFAULT_TENANT_UUID));

      // Assert
      assertThat(pathRouteOfA.readUntilPings())
          .contains(tagOfA)
          .doesNotContain(tagOfB)
          .doesNotContain(tagOfDefault);
      assertThat(pathRouteOfB.readUntilPings())
          .contains(tagOfB)
          .doesNotContain(tagOfA)
          .doesNotContain(tagOfDefault);
      assertThat(headerRouteNamingB.readUntilPings())
          .as("the route without a tenant streams the default tenant, whatever the header names")
          .contains(tagOfDefault)
          .doesNotContain(tagOfB)
          .doesNotContain(tagOfA);
    }
  }

  private BaseEvent eventOfTag(String tagId, String tenantId) {
    Tag tag = new Tag();
    tag.setId(tagId);
    tag.setName("stream-route-" + tagId);
    tag.setTenant(new Tenant(tenantId));
    return new BaseEvent(DATA_UPDATE, tag, mapper);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> consumers() {
    return (Map<String, Object>) ReflectionTestUtils.getField(streamService, "consumers");
  }

  /**
   * Opens a stream as the platform admin, a member of both tenants, reads it up to its first ping,
   * so that the subscription is registered when it returns, and checks the tenant of the consumer
   * it registered.
   *
   * @param tenantHeader the tenant the {@code X-Tenant-Ids} header names, or null for none
   * @param expectedTenant the tenant the consumer must receive the events of
   */
  private Subscriber subscribe(String uri, String tenantHeader, String expectedTenant)
      throws IOException {
    Map<String, Object> before = new HashMap<>(consumers());
    Socket socket = new Socket("localhost", port);
    sockets.add(socket);
    socket.setSoTimeout((int) READ_TIMEOUT.toMillis());
    OutputStream out = socket.getOutputStream();
    out.write(
        ("GET "
                + uri
                + " HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Accept: text/event-stream\r\n"
                + (tenantHeader == null ? "" : TENANT_HEADER + ": " + tenantHeader + "\r\n")
                + "Authorization: Bearer "
                + adminToken
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.flush();
    BufferedReader in =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    assertThat(in.readLine()).as("the response to %s", uri).startsWith("HTTP/1.1 200");
    String line;
    while ((line = in.readLine()) != null && !line.startsWith("event:ping")) {
      // skip the headers until the first ping
    }
    assertThat(line).as("the first ping of %s", uri).isEqualTo("event:ping");
    List<Object> registered =
        consumers().entrySet().stream()
            .filter(entry -> before.get(entry.getKey()) != entry.getValue())
            .map(Map.Entry::getValue)
            .toList();
    assertThat(registered).as("the consumers %s registered", uri).hasSize(1);
    assertThat((String) ReflectionTestUtils.invokeMethod(registered.getFirst(), "tenantId"))
        .as("the tenant of the consumer of %s", uri)
        .isEqualTo(expectedTenant);
    return new Subscriber(in);
  }

  private record Subscriber(BufferedReader in) {

    /**
     * What the stream carries up to its next pings: a ping is sent every second on the same
     * connection, after the events delivered before it.
     */
    String readUntilPings() throws IOException {
      StringBuilder received = new StringBuilder();
      int pings = 0;
      String line;
      while (pings < PINGS_AFTER_DELIVERY && (line = in.readLine()) != null) {
        if (line.startsWith("event:ping")) {
          pings++;
        }
        received.append(line).append('\n');
      }
      return received.toString();
    }
  }
}
