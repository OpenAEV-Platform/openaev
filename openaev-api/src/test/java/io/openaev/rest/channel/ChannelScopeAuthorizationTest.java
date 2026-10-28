package io.openaev.rest.channel;

import static io.openaev.rest.channel.ChannelApi.CHANNEL_URI;
import static io.openaev.rest.exception.TenantScopeForbiddenException.MESSAGE;
import static io.openaev.utils.fixtures.ChannelFixture.getChannel;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Channel;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.ChannelComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins that a read on the non-prefixed route with no selector, a list or a read by id, requires the
 * read right in every tenant of the request scope, which is then the caller's whole membership. The
 * caller is not an admin and is a member of the default tenant and of a tenant B, which owns the
 * channel. Channels need a capability to be read, unlike the open resources such as tags.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("Channel reads are authorized in every tenant of the request scope")
class ChannelScopeAuthorizationTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String CHANNEL_BY_ID_URI = CHANNEL_URI + "/{channelId}";
  private static final Set<Capability> READ_RIGHT = Set.of(Capability.ACCESS_CHANNELS);
  private static final Set<Capability> ANOTHER_RIGHT = Set.of(Capability.ACCESS_ASSESSMENT);

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ChannelComposer channelComposer;

  enum Read {
    LIST,
    BY_ID;

    MockHttpServletRequestBuilder request(String channelId) {
      return switch (this) {
        case LIST -> get(CHANNEL_URI);
        case BY_ID -> get(CHANNEL_BY_ID_URI, channelId);
      };
    }
  }

  @BeforeEach
  void resetComposers() {
    channelComposer.reset();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Read right in the default tenant only, another right in tenant B")
  class ReadRightInDefaultTenantOnly {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Read.class)
    @DisplayName("No selector: the scope is every membership, so the read is refused")
    void given_noSelector_should_requireTheReadRightInEveryScopeTenant(Read read) throws Exception {
      // Arrange
      String channelId = seedChannelInTenantB(ANOTHER_RIGHT);

      // Act
      MockHttpServletResponse response = perform(read.request(channelId));

      // Assert
      assertAll(
          () -> assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus(), "refused"),
          () ->
              assertEquals(
                  MESSAGE,
                  JsonPath.read(response.getContentAsString(), "$.message"),
                  "the body names the scope rule and its remedy"));
    }
  }

  @Nested
  @DisplayName("Read right in both the default tenant and tenant B")
  class ReadRightInEveryTenant {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Read.class)
    @DisplayName("No selector: the read is allowed and reaches tenant B's channel")
    void given_noSelectorAndTheReadRightInEveryScopeTenant_should_returnTheChannel(Read read)
        throws Exception {
      // Arrange
      String channelId = seedChannelInTenantB(READ_RIGHT);

      // Act
      MockHttpServletResponse response = perform(read.request(channelId));

      // Assert
      assertAll(
          () -> assertEquals(HttpStatus.OK.value(), response.getStatus(), "the read is allowed"),
          () ->
              assertTrue(
                  response.getContentAsString().contains(channelId),
                  "B's channel is in the response"));
    }
  }

  /**
   * The caller holds the read right in the default tenant and {@code rightsInB} in a new tenant B,
   * which owns the returned channel.
   */
  private String seedChannelInTenantB(Set<Capability> rightsInB) throws Exception {
    tenantHelper.grantCapabilitiesInTenant(DEFAULT_TENANT, READ_RIGHT);
    String tenantB =
        tenantHelper.createTenantWithCapabilities("channel-scope-b", rightsInB).getId();
    Channel channel = getChannel();
    channel.setTenant(new Tenant(tenantB));
    String channelId = channelComposer.forChannel(channel).persist().get().getId();
    entityManager.flush();
    assertEquals(tenantB, tenantOf(channelId), "precondition: the channel lives in tenant B");
    return channelId;
  }

  private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
    // The non-prefixed route never sets the ambient tenant: reproduce production's empty
    // thread-local instead of inheriting what the fixtures left on the test thread.
    TenantContext.clearCurrentTenant();
    assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");
    return mvc.perform(request).andReturn().getResponse();
  }

  /**
   * Reads on the current connection, past the statement inspector, so that the row is seen whatever
   * scope the fixtures left on the test transaction.
   */
  private String tenantOf(String channelId) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM channels WHERE channel_id = ?")) {
                stmt.setString(1, channelId);
                try (var rows = stmt.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }
}
