package io.openaev.rest.document;

import static io.openaev.rest.document.DocumentApi.DOCUMENT_API;
import static io.openaev.rest.document.DocumentApi.TENANT_DOCUMENT_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The document tag endpoints link a document to the tags of the document's own tenant.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants, and the repository
 * scope then holds all of them: a lookup of tag ids in that scope returns the tags of every tenant
 * of the header. The links are read from the join table through JDBC on the test connection, which
 * the statement inspector does not rewrite. The class arms the production list, so {@code tags} and
 * {@code documents} are both scoped by the inspector.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Document tag endpoints link only the tags of the document tenant")
class DocumentTagLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TagRepository tagRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String documentInB;
  private String tagInB;
  private String tagInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("document-tag-link-b").getId();
    tagInB = saveTag(tenantB);
    tagInDefault = saveTag(Tenant.DEFAULT_TENANT_UUID);
    documentInB = saveDocumentInB();
    entityManager.flush();
    entityManager.clear();
    // Onboarding leaves B on the test thread, while the request thread of the header route carries
    // none: the ambient tenant must be the default one here.
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Header route naming two tenants")
  class HeaderRouteNamingTwoTenants {

    @Test
    @DisplayName(
        "given a tag update of a document of B when the header names the default tenant and B"
            + " then only the tag of B is linked")
    void given_tagsOfTwoTenants_should_linkOnlyTheTagsOfTheDocumentTenant() throws Exception {
      // -- Arrange --
      assertSeedTenants();

      // -- Act --
      mvc.perform(
              put(DOCUMENT_API + "/" + documentInB + "/tags")
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("tags", bothTags())))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(linkedTags())
          .as("tags linked to the document of B: kept %s, dropped %s", tagInB, tagInDefault)
          .containsExactly(tagInB);
    }

    @Test
    @DisplayName(
        "given an update of a document of B when the header names the default tenant and B and"
            + " the body names a tag of each then only the tag of B is linked")
    void given_documentUpdateWithTagsOfTwoTenants_should_linkOnlyTheTagsOfTheDocumentTenant()
        throws Exception {
      // -- Arrange --
      assertSeedTenants();

      // -- Act --
      mvc.perform(
              put(DOCUMENT_API + "/" + documentInB)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "document_tags", bothTags(),
                              "document_exercises", List.of(),
                              "document_scenarios", List.of())))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(linkedTags())
          .as("tags linked to the document of B: kept %s, dropped %s", tagInB, tagInDefault)
          .containsExactly(tagInB);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a tag update under the path of B when the body names a tag of each tenant then"
            + " the tag of B is linked")
    void given_tagsOfTwoTenantsOnPrefixedRoute_should_linkTheTagOfTheDocumentTenant()
        throws Exception {
      // -- Arrange --
      assertSeedTenants();

      // -- Act --
      mvc.perform(
              put(TENANT_DOCUMENT_API + "/" + documentInB + "/tags", tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(Map.of("tags", bothTags())))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(linkedTags())
          .as("tags linked to the document of B: kept %s, dropped %s", tagInB, tagInDefault)
          .containsExactly(tagInB);
    }
  }

  // -- Helpers --

  private List<String> bothTags() {
    return List.of(tagInB, tagInDefault);
  }

  private String saveTag(String tenantId) {
    Tag tag = TagFixture.getTagWithText("document-tag-link-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenantId));
    return tagRepository.save(tag).getId();
  }

  private String saveDocumentInB() {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setTenant(new Tenant(tenantB));
    document.setTarget("document-tag-link-" + UUID.randomUUID() + ".jpeg");
    document.setName("document-tag-link-" + UUID.randomUUID() + ".jpeg");
    return documentRepository.save(document).getId();
  }

  /** Each tag and the document carry the tenant the scenario relies on. */
  private void assertSeedTenants() {
    assertThat(column("SELECT tenant_id FROM tags WHERE tag_id = ?", tagInB))
        .containsExactly(tenantB);
    assertThat(column("SELECT tenant_id FROM tags WHERE tag_id = ?", tagInDefault))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    assertThat(column("SELECT tenant_id FROM documents WHERE document_id = ?", documentInB))
        .containsExactly(tenantB);
  }

  private List<String> linkedTags() {
    return column(
        "SELECT tag_id FROM documents_tags WHERE document_id = ? ORDER BY 1", documentInB);
  }

  /** JDBC on the test connection: the statement inspector does not rewrite it. */
  private List<String> column(String sql, String parameter) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<String> values = new ArrayList<>();
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet resultSet = statement.executeQuery()) {
                  while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                  }
                }
              }
              return values;
            });
  }
}
