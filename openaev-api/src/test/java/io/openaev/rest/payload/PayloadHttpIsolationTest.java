package io.openaev.rest.payload;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Command;
import io.openaev.database.model.Document;
import io.openaev.database.model.Executable;
import io.openaev.database.model.Tenant;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
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
 * End-to-end proof that, with {@code payloads} activated, the tenant scope set from the URL path
 * (or the {@code X-Tenant-Ids} header) isolates the table through the real {@link PayloadApi}
 * endpoints. A user who belongs to two tenants sees a payload only under its own tenant's path,
 * never another tenant's; a create is attributed to the write scope, never to an ambient default.
 *
 * <p>Each test stays on a single tenant path so the per-request scope is set once: re-applying the
 * same scope inside the test transaction is tolerated, changing it would hit the nesting guard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=payloads,documents")
@WithMockUser(isAdmin = true)
@DisplayName("payloads read and write isolation through the real HTTP endpoint")
class PayloadHttpIsolationTest extends IntegrationTest {

  private static final String PAYLOAD_BY_ID = "/api/tenants/{tenantId}/payloads/{payloadId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  private String tenantA;
  private String tenantB;
  private String payloadA;
  private String payloadB;

  @BeforeEach
  void seedTwoTenantsWithOnePayloadEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("http-iso-b").getId();
    payloadA = seedCommandPayload(tenantA, "payload-a");
    payloadB = seedCommandPayload(tenantB, "payload-b");
  }

  @Test
  @DisplayName("under tenant A's path: A's payload is visible, B's is hidden")
  void underTenantAPath() throws Exception {
    mvc.perform(get(PAYLOAD_BY_ID, tenantA, payloadA)).andExpect(status().isOk());
    mvc.perform(get(PAYLOAD_BY_ID, tenantA, payloadB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's payload is visible, A's is hidden")
  void underTenantBPath() throws Exception {
    mvc.perform(get(PAYLOAD_BY_ID, tenantB, payloadB)).andExpect(status().isOk());
    mvc.perform(get(PAYLOAD_BY_ID, tenantB, payloadA)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header (no path tenant): search returns A's payload, not B's")
  void searchViaHeaderReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post("/api/payloads/search")
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains("payload-a"), "A's payload must appear when A is selected via header");
    assertTrue(!response.contains("payload-b"), "B's payload must not appear");
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's payload and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/payloads/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains("payload-a"), "A's payload must appear in A's search results");
    assertTrue(
        !response.contains("payload-b"), "B's payload must not appear in A's search results");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(Collections.emptyList());
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/payloads", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdId = JsonPath.read(response, "$.payload_id");
    assertEquals(tenantA, rawTenant(createdId), "the created payload must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(Collections.emptyList());
    mvc.perform(
            post("/api/payloads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant A's path: A can update its own payload")
  void updateUnderTenantAUpdatesOwnPayload() throws Exception {
    PayloadUpdateInput input =
        PayloadInputFixture.getDefaultCommandPayloadUpdateInput(Collections.emptyList());
    mvc.perform(
            put(PAYLOAD_BY_ID, tenantA, payloadA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals(
        "Updated Command line payload", rawName(payloadA), "A's own payload must be updated");
  }

  @Test
  @DisplayName("under tenant A's path: updating B's payload is not found and leaves it untouched")
  void updateUnderTenantAOfBPayloadIsBlocked() throws Exception {
    PayloadUpdateInput input =
        PayloadInputFixture.getDefaultCommandPayloadUpdateInput(Collections.emptyList());
    mvc.perform(
            put(PAYLOAD_BY_ID, tenantA, payloadB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("payload-b", rawName(payloadB), "B's payload must be untouched by tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: A can delete its own payload")
  void deleteUnderTenantADeletesOwnPayload() throws Exception {
    mvc.perform(delete(PAYLOAD_BY_ID, tenantA, payloadA).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(0L, rawCount(payloadA), "A's own payload must be deleted");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's payload is not found and leaves it in place")
  void deleteUnderTenantAOfBPayloadIsBlocked() throws Exception {
    mvc.perform(delete(PAYLOAD_BY_ID, tenantA, payloadB).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount(payloadB), "B's payload must survive tenant A's delete attempt");
  }

  @Test
  @DisplayName("under tenant A's path: duplicating A's payload attributes the copy to tenant A")
  void duplicateUnderTenantAIsAttributedToA() throws Exception {
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/payloads/{payloadId}/duplicate", tenantA, payloadA)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String copyId = JsonPath.read(response, "$.payload_id");
    assertThat(copyId).isNotEqualTo(payloadA);
    assertEquals(tenantA, rawTenant(copyId), "the duplicated payload must belong to tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: duplicating B's payload is not found (cross-tenant blocked)")
  void duplicateUnderTenantAOfBPayloadIsBlocked() throws Exception {
    mvc.perform(
            post("/api/tenants/{tenantId}/payloads/{payloadId}/duplicate", tenantA, payloadB)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "under tenant A's path: an executable payload whose file belongs to tenant B does not"
          + " leak B's document and does not crash the read")
  void executableWithCrossTenantDocumentDoesNotLeakOrCrash() throws Exception {
    // The payload read serves the executable's file as an id: PayloadOutput.executableFile is a
    // plain String, and PayloadMapper.toPayloadOutput extracts the document id into it. A name is
    // never serialized here, so the subject of both assertions is that id.
    // Positive control first, on A's own document: without it an empty field would satisfy the
    // cross-tenant assertion whatever the scope did.
    Document ownDocument = DocumentFixture.getDocumentJpeg();
    ownDocument.setTenant(new Tenant(tenantA));
    entityManager.persist(ownDocument);
    Executable ownExecutable = (Executable) PayloadFixture.createDefaultExecutable(ownDocument);
    // The fixture carries a fixed id, so the two executables of this test need distinct ones.
    ownExecutable.setId(UUID.randomUUID().toString());
    ownExecutable.setName("executable-with-a-document");
    ownExecutable.setTenant(new Tenant(tenantA));
    entityManager.persist(ownExecutable);

    Document bDocument = DocumentFixture.getDocumentJpeg();
    bDocument.setTenant(new Tenant(tenantB));
    entityManager.persist(bDocument);
    Executable executable = (Executable) PayloadFixture.createDefaultExecutable(bDocument);
    executable.setId(UUID.randomUUID().toString());
    executable.setName("executable-with-b-document");
    executable.setTenant(new Tenant(tenantA));
    entityManager.persist(executable);
    // Flush and clear so the reads below issue real SELECTs instead of being answered from this
    // transaction's first-level cache, which no scope applies to.
    entityManager.flush();
    entityManager.clear();

    String ownResponse =
        mvc.perform(get(PAYLOAD_BY_ID, tenantA, ownExecutable.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(
        ownDocument.getId(),
        JsonPath.read(ownResponse, "$.executable_file"),
        "A's own executable must serve its own document id, otherwise the assertion below is"
            + " satisfied by a field that is never populated");

    String response =
        mvc.perform(get(PAYLOAD_BY_ID, tenantA, executable.getId()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        !response.contains(bDocument.getId()),
        "B's document id must never appear in A's payload read: " + response);
  }

  // Ground-truth reads, bypassing the scope: raw JDBC on the test's own connection sees the
  // uncommitted seed and the rewriter does not touch a statement it never generated. A flush first
  // forces any pending scoped UPDATE/DELETE to reach the database.
  private String rawName(String payloadId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT payload_name FROM payloads WHERE payload_id = ? ")) {
                statement.setString(1, payloadId);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private String rawTenant(String payloadId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM payloads WHERE payload_id = ? ")) {
                statement.setString(1, payloadId);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String payloadId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM payloads WHERE payload_id = ? ")) {
                statement.setString(1, payloadId);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private String seedCommandPayload(String tenantId, String name) {
    Command command = (Command) PayloadFixture.createCommand("bash", "echo hello", List.of(), null);
    command.setName(name);
    command.setTenant(new Tenant(tenantId));
    entityManager.persist(command);
    entityManager.flush();
    // Evict from the session's identity map: a later findById-by-id must go through a real
    // SELECT (and so through the rewriter), not return this Java object straight from the L1
    // cache.
    entityManager.clear();
    return command.getId();
  }
}
