package io.openaev.rest.injector;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.rest.injector.form.InjectorCreateInput;
import io.openaev.rest.injector.form.InjectorUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=injectors")
@WithMockUser(isAdmin = true)
@DisplayName("injectors read and write isolation through the real HTTP endpoint")
class InjectorHttpIsolationTest extends IntegrationTest {

  private static final String INJECTOR_BY_ID = "/api/tenants/{tenantId}/injectors/{injectorId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String injectorA;
  private String injectorB;

  @BeforeEach
  void seedTwoTenantsWithOneInjectorEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("http-iso-b").getId();
    injectorA = seedInjector(tenantA, "injector-a", "type-a");
    injectorB = seedInjector(tenantB, "injector-b", "type-b");
  }

  @Test
  @DisplayName("under tenant A's path: A's injector is visible, B's is hidden")
  void underTenantAPath() throws Exception {
    mvc.perform(get(INJECTOR_BY_ID, tenantA, injectorA)).andExpect(status().isOk());
    mvc.perform(get(INJECTOR_BY_ID, tenantA, injectorB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's injector is visible, A's is hidden")
  void underTenantBPath() throws Exception {
    mvc.perform(get(INJECTOR_BY_ID, tenantB, injectorB)).andExpect(status().isOk());
    mvc.perform(get(INJECTOR_BY_ID, tenantB, injectorA)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: list returns only A's injector")
  void listViaHeaderReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get("/api/injectors").header("X-Tenant-Ids", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(injectorA), "A's injector must appear when A is selected");
    assertFalse(response.contains(injectorB), "B's injector must not appear");
  }

  @Test
  @DisplayName("under tenant A's path: list returns only A's injector")
  void listUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get("/api/tenants/{tenantId}/injectors", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(injectorA), "A's injector must appear");
    assertFalse(response.contains(injectorB), "B's injector must not appear");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    String newId = UUID.randomUUID().toString();
    InjectorCreateInput input = new InjectorCreateInput();
    input.setId(newId);
    input.setName("created-under-a");
    input.setType("test-create-type");
    input.setContracts(List.of());
    input.setCustomContracts(false);
    input.setPayloads(false);

    MockMultipartFile inputPart =
        new MockMultipartFile(
            "input",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            asJsonString(input).getBytes(StandardCharsets.UTF_8));

    mvc.perform(
            multipart("/api/tenants/{tenantId}/injectors", tenantA).file(inputPart).with(csrf()))
        .andExpect(status().isOk());

    String storedTenant = rawTenantId(newId);
    assertEquals(tenantA, storedTenant, "the created injector must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (single-tenant scope required)")
  void createWithoutSelectorIsRejected() throws Exception {
    InjectorCreateInput input = new InjectorCreateInput();
    input.setId(UUID.randomUUID().toString());
    input.setName("no-selector");
    input.setType("test-noselector-type");
    input.setContracts(List.of());
    input.setCustomContracts(false);
    input.setPayloads(false);

    MockMultipartFile inputPart =
        new MockMultipartFile(
            "input",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            asJsonString(input).getBytes(StandardCharsets.UTF_8));

    mvc.perform(multipart("/api/injectors").file(inputPart).with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant A's path: updating B's injector is not found")
  void updateUnderTenantAOfBInjectorIsBlocked() throws Exception {
    InjectorUpdateInput input = new InjectorUpdateInput();
    input.setName("updated");
    input.setContracts(List.of());
    input.setCustomContracts(false);
    input.setPayloads(false);

    mvc.perform(
            put(INJECTOR_BY_ID, tenantA, injectorB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("deleting B's injector under tenant A's path is blocked")
  void deleteUnderTenantAOfBInjectorIsNoOp() throws Exception {
    mvc.perform(delete(INJECTOR_BY_ID, tenantA, injectorB).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount(injectorB), "B's injector must not have been deleted");
  }

  @Test
  @DisplayName("deleting with an ambiguous multi-tenant scope is refused")
  void deleteWithAmbiguousScopeIsRejected() throws Exception {
    mvc.perform(
            delete("/api/injectors/{injectorId}", injectorA)
                .header("X-Tenant-Ids", tenantA + "," + tenantB)
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertEquals(1L, rawCount(injectorA), "A's injector must not have been deleted");
  }

  @Test
  @DisplayName("deleting under a single-tenant header scope removes the injector")
  void deleteWithSingleTenantHeaderScopeSucceeds() throws Exception {
    mvc.perform(
            delete("/api/injectors/{injectorId}", injectorA)
                .header("X-Tenant-Ids", tenantA)
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals(0L, rawCount(injectorA), "A's injector must have been deleted");
  }

  private String rawTenantId(String injectorId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement stmt =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM injectors WHERE injector_id = ?")) {
                stmt.setString(1, injectorId);
                try (ResultSet rs = stmt.executeQuery()) {
                  return rs.next() ? rs.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String injectorId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement stmt =
                  connection.prepareStatement(
                      "SELECT count(*) FROM injectors WHERE injector_id = ?")) {
                stmt.setString(1, injectorId);
                try (ResultSet rs = stmt.executeQuery()) {
                  rs.next();
                  return rs.getLong(1);
                }
              }
            });
  }

  private String seedInjector(String tenantId, String name, String type) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO injectors (injector_id, tenant_id, injector_name, injector_type,"
                + " injector_external, injector_custom_contracts, injector_payloads,"
                + " injector_created_at, injector_updated_at)"
                + " VALUES (:id, :tenant, :name, :type, false, false, false, now(), now())")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("name", name)
        .setParameter("type", type)
        .executeUpdate();
    return id;
  }
}
