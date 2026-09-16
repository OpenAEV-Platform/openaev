package io.openaev.rest.channel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The data isolation must not depend on the caller being an administrator. This test runs as a
 * non-admin that is a member of two tenants and holds only the channel read capability, and proves
 * the rewriter still returns one tenant's channels under its path.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=channels")
@WithMockUser(isAdmin = false)
@DisplayName("channels isolation holds for a non-admin spanning two tenants")
class ChannelNonAdminIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String channelA;
  private String channelB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneChannelEach() throws Exception {
    tenantA =
        tenantHelper
            .createTenantWithCapabilities("nonadmin-channel-a", Set.of(Capability.ACCESS_CHANNELS))
            .getId();
    String tenantB =
        tenantHelper
            .createTenantWithCapabilities("nonadmin-channel-b", Set.of(Capability.ACCESS_CHANNELS))
            .getId();
    channelA = seedChannel(tenantA, "nonadmin-channel-a");
    channelB = seedChannel(tenantB, "nonadmin-channel-b");
  }

  @Test
  @DisplayName("a non-admin listing under tenant A's path sees only A's channel")
  void listUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String response =
        mvc.perform(get("/api/tenants/{tenantId}/channels", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(channelA), "A's channel must appear for the non-admin member of A");
    assertFalse(response.contains(channelB), "B's channel must not leak to A's scope");
  }

  private String seedChannel(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO channels"
                + " (channel_id, channel_name, channel_type, channel_description, channel_created_at,"
                + " channel_updated_at, tenant_id)"
                + " VALUES (CAST(:id AS uuid), :name, :type, :description, now(), now(),"
                + " CAST(:tenant AS uuid))")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("type", "Journal")
        .setParameter("description", "Seeded channel")
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }
}
