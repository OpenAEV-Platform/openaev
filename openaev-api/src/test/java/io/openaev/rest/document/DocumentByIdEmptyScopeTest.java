package io.openaev.rest.document;

import static io.openaev.rest.document.DocumentApi.DOCUMENT_API;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * With {@code documents} v2-active, a caller that resolves no tenant scope reads no document. On
 * the non-prefixed route a caller with no tenant membership and no {@code X-Tenant-Ids} selector
 * resolves an empty scope ({@code TxCtx.missing()}), which {@code TenantScopeTransactionAspect}
 * serializes as an empty {@code app.current_tenants}; {@code can_access_tenant} then denies every
 * row, including the primary-key {@code findById} that loads a document by id. The by-id endpoints
 * therefore answer the same 404 as for a missing document, and a refused write leaves the target
 * row unchanged. This class pins that fail-closed rule for the read and the tags update on the
 * non-prefixed route.
 *
 * <p>The class is NOT {@code @Transactional}: the row must be committed and out of any live
 * persistence context so the request's {@code findById} issues real SQL and reaches the statement
 * inspector. A managed entity would be a first-level-cache hit that never queries the database, so
 * the endpoint would answer 200 whatever the scope is and the test would prove nothing. Seeding
 * runs in a committed {@code TransactionTemplate} and is removed by {@code tenant_id} in teardown,
 * the shape {@link DocumentHttpIsolationTest} and {@code ReportingScheduleDocumentScopeTest}
 * already use on this branch.
 *
 * <p>{@code @TestPropertySource} activates {@code documents} for this test only (the test classpath
 * keeps the allowlist empty, so without it the inspector is inert and the endpoints would return
 * the out-of-scope document, which is the behaviour before activation). {@code @WithMockUser}
 * builds a user with no row in {@code users_tenants}, so its scope resolves to {@code
 * TxCtx.missing()}. The owning tenant is inserted as a bare row rather than through {@code
 * TenantService.create}, whose onboarding would enroll the platform admin in it and turn the
 * resolved scope into a single-tenant one; keeping the caller member-less is what makes the request
 * scope genuinely empty. The ground-truth tag count goes through raw JDBC ({@link JdbcTemplate}),
 * which the Hibernate statement inspector never rewrites, so a request scope leaked into the
 * connection cannot mask the survival check.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("Document by-id endpoints fail closed when the request scope is empty")
class DocumentByIdEmptyScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String otherTenant;
  private String plainDocumentId;
  private String taggedDocumentId;

  @BeforeEach
  void seedNonMemberTenantWithDocuments() {
    jdbc = new JdbcTemplate(dataSource);
    // A bare tenant row, not an onboarded one: onboarding would enroll the platform admin and give
    // the caller a single-tenant scope. Persisting it directly keeps the caller member-less, so a
    // request with no selector resolves an empty scope. Everything is committed so the request's
    // by-id load issues real SQL against the database rather than hitting a persistence-context
    // row.
    rawTransaction()
        .execute(
            status -> {
              Tenant tenant = TenantFixture.getTenant("doc-empty-scope-" + UUID.randomUUID());
              entityManager.persist(tenant);
              otherTenant = tenant.getId();

              Document plain = newDocument(otherTenant);
              entityManager.persist(plain);
              plainDocumentId = plain.getId();

              // The tagged document carries one tag, so a write that went through (setting empty
              // tags) would drop the count to zero; a refused write leaves it at one.
              Tag tag = TagFixture.getTagWithText("doc-empty-tag-" + UUID.randomUUID());
              tag.setTenant(new Tenant(otherTenant));
              entityManager.persist(tag);
              Document tagged = newDocument(otherTenant);
              tagged.setTags(Set.of(tag));
              entityManager.persist(tagged);
              taggedDocumentId = tagged.getId();

              entityManager.flush();
              return null;
            });
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM documents_tags WHERE document_id = ?", taggedDocumentId);
    jdbc.update("DELETE FROM documents WHERE tenant_id = ?", otherTenant);
    jdbc.update("DELETE FROM tags WHERE tenant_id = ?", otherTenant);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", otherTenant);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("The by-id read is refused a document the empty-scope caller cannot see")
  class Reads {

    @Test
    @DisplayName(
        "given an empty request scope should return 404 on GET by id for another tenant's document")
    void given_emptyRequestScope_should_return404OnGetById() throws Exception {
      // Act & Assert
      mvc.perform(get(DOCUMENT_API + "/{id}", plainDocumentId)).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("The by-id tags update is refused and leaves the row unchanged")
  class Writes {

    @Test
    @DisplayName(
        "given an empty request scope should return 404 on PUT tags and leave the document tags"
            + " unchanged")
    void given_emptyRequestScope_should_return404AndNotClearTagsOnPutTags() throws Exception {
      // Act
      mvc.perform(
              put(DOCUMENT_API + "/{id}/tags", taggedDocumentId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"tags\":[]}")
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(
          1,
          documentTagCount(taggedDocumentId),
          "the refused tags write must not clear the document tags");
    }
  }

  private Document newDocument(String tenantId) {
    Document document = new Document();
    document.setName("doc-empty-" + UUID.randomUUID());
    document.setTarget(UUID.randomUUID() + ".txt");
    document.setType(MediaType.TEXT_PLAIN_VALUE);
    document.setTenant(new Tenant(tenantId));
    return document;
  }

  /**
   * Counts the document's tags with raw JDBC on a pooled connection. The Hibernate statement
   * inspector rewrites only Hibernate-issued SQL, so this reads the true ground truth even if the
   * request left an empty scope on its connection.
   */
  private long documentTagCount(String documentId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM documents_tags WHERE document_id = ?", Long.class, documentId);
    return count == null ? 0L : count;
  }

  private TransactionTemplate rawTransaction() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    return template;
  }
}
