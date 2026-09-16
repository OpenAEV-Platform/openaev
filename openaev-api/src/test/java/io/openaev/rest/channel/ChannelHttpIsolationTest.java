package io.openaev.rest.channel;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.rest.channel.form.ChannelCreateInput;
import io.openaev.rest.channel.form.ChannelUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code channels} activated, the tenant scope set from the request
 * isolates the table through the real {@link ChannelApi} endpoints. A user who belongs to two
 * tenants sees a channel only under its own tenant's path, never another tenant's.
 *
 * <p>Each test stays on a single tenant path so the per-request scope is set once: re-applying the
 * same scope inside the test transaction is tolerated, changing it would hit the nesting guard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=channels")
@WithMockUser(isAdmin = true)
@DisplayName("channels read and write isolation through the real HTTP endpoint")
class ChannelHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_CHANNELS = "/api/tenants/{tenantId}/channels";
  private static final String TENANT_CHANNEL_BY_ID = TENANT_CHANNELS + "/{channelId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String channelA;
  private String channelB;

  @BeforeEach
  void seedTwoTenantsWithOneChannelEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("channel-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("channel-iso-b").getId();
    channelA = seedChannel(tenantA, "channel-a");
    channelB = seedChannel(tenantB, "channel-b");
  }

  @Test
  @DisplayName("under tenant A's path: A's channel is visible, B's is hidden")
  void underTenantAPath() throws Exception {
    mvc.perform(get(TENANT_CHANNEL_BY_ID, tenantA, channelA)).andExpect(status().isOk());
    mvc.perform(get(TENANT_CHANNEL_BY_ID, tenantA, channelB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's channel is visible, A's is hidden")
  void underTenantBPath() throws Exception {
    mvc.perform(get(TENANT_CHANNEL_BY_ID, tenantB, channelB)).andExpect(status().isOk());
    mvc.perform(get(TENANT_CHANNEL_BY_ID, tenantB, channelA)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: list returns only A's channel")
  void listViaHeaderReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get("/api/channels").header("X-Tenant-Ids", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(channelA), "A's channel must appear when A is selected via header");
    assertFalse(response.contains(channelB), "B's channel must not appear");
  }

  @Test
  @DisplayName("under tenant A's path: list returns A's channel and not B's")
  void listUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(TENANT_CHANNELS, tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(channelA), "A's channel must appear in A's list");
    assertFalse(response.contains(channelB), "B's channel must not appear in A's list");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    String response =
        mvc.perform(
                post(TENANT_CHANNELS, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(createInput("created-under-a")))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdId = JsonPath.read(response, "$.channel_id");
    String storedTenant =
        (String)
            entityManager
                .createNativeQuery("SELECT tenant_id FROM channels WHERE channel_id = :id")
                .setParameter("id", createdId)
                .getSingleResult();
    assertEquals(tenantA, storedTenant, "the created channel must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    mvc.perform(
            post("/api/channels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(createInput("no-selector")))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant A's path: A can update its own channel")
  void updateUnderTenantAUpdatesOwnChannel() throws Exception {
    mvc.perform(
            put(TENANT_CHANNEL_BY_ID, tenantA, channelA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput("renamed-a")))
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals("renamed-a", rawName(channelA), "A's own channel must be updated");
  }

  @Test
  @DisplayName("under tenant A's path: updating B's channel is not found and leaves it untouched")
  void updateUnderTenantAOfBChannelIsBlocked() throws Exception {
    mvc.perform(
            put(TENANT_CHANNEL_BY_ID, tenantA, channelB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput("hijacked")))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("channel-b", rawName(channelB), "B's channel must be untouched");
  }

  @Test
  @DisplayName("under tenant A's path: A can delete its own channel")
  void deleteUnderTenantADeletesOwnChannel() throws Exception {
    mvc.perform(delete(TENANT_CHANNEL_BY_ID, tenantA, channelA).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(0L, rawCount(channelA), "A's own channel must be deleted");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's channel is a no-op and leaves it in place")
  void deleteUnderTenantAOfBChannelIsBlocked() throws Exception {
    mvc.perform(delete(TENANT_CHANNEL_BY_ID, tenantA, channelB).with(csrf()));
    assertEquals(1L, rawCount(channelB), "B's channel must survive tenant A's delete attempt");
  }

  private static ChannelCreateInput createInput(String name) {
    ChannelCreateInput input = new ChannelCreateInput();
    input.setName(name);
    input.setType("Journal");
    input.setDescription("Channel description");
    return input;
  }

  private static ChannelUpdateInput updateInput(String name) {
    ChannelUpdateInput input = new ChannelUpdateInput();
    input.setName(name);
    input.setType("Journal");
    input.setDescription("Updated description");
    return input;
  }

  private String seedChannel(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO channels"
                + " (channel_id, channel_name, channel_type, channel_description, channel_created_at,"
                + " channel_updated_at, tenant_id)"
                + " VALUES (:id, :name, :type, :description, now(), now(),"
                + " :tenant)")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("type", "Journal")
        .setParameter("description", "Seeded channel")
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private String rawName(String channelId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT channel_name FROM channels WHERE channel_id = ?")) {
                statement.setString(1, channelId);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String channelId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM channels WHERE channel_id = ?")) {
                statement.setString(1, channelId);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
