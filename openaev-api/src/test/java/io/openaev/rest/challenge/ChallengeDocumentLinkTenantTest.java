package io.openaev.rest.challenge;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.challenge.ChallengeApi.CHALLENGE_URI;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ChallengeFixture;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.mockUser.WithMockUser;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The challenge update links the challenge to the documents of the challenge's own tenant.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants. The document ids of
 * the body resolve in the tenant of the challenge alone. The links are read from {@code
 * challenges_documents} through JDBC on the test connection, which the statement inspector does not
 * rewrite. The class arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Challenge update links only the documents of the challenge tenant")
class ChallengeDocumentLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String CHALLENGES_DOCUMENTS =
      "SELECT document_id FROM challenges_documents WHERE challenge_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String documentInB;
  private String documentInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("challenge-document-link-b").getId();
    documentInB = saveDocument(tenantB);
    documentInDefault = saveDocument(Tenant.DEFAULT_TENANT_UUID);
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
        "given a challenge of B when the body names a document of each tenant then only the"
            + " document of B is linked")
    void given_challengeOfB_should_linkOnlyTheDocumentOfB() throws Exception {
      // -- Arrange --
      String challenge = saveChallengeInB();
      armRequest(challenge);

      // -- Act --
      putOnHeaderRoute(challenge, challengeBody(List.of(documentInB, documentInDefault)))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CHALLENGES_DOCUMENTS, challenge))
          .as(
              "documents linked to the challenge of B: kept %s, dropped %s",
              documentInB, documentInDefault)
          .containsExactly(documentInB);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a challenge of B when the body under the path of B names a document of B then the"
            + " document is linked")
    void given_challengeOfB_should_linkTheDocumentOfBOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      String challenge = saveChallengeInB();
      armRequest(challenge);

      // -- Act --
      mvc.perform(
              put(TENANT_PREFIX + "/challenges/{id}", tenantB, challenge)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(challengeBody(List.of(documentInB))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CHALLENGES_DOCUMENTS, challenge))
          .containsExactly(documentInB);
    }
  }

  // -- Helpers --

  private ResultActions putOnHeaderRoute(String challenge, Map<String, Object> body)
      throws Exception {
    return mvc.perform(
        put(CHALLENGE_URI + "/" + challenge)
            .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))
            .with(csrf()));
  }

  private Map<String, Object> challengeBody(List<String> documents) {
    Map<String, Object> body = new HashMap<>();
    body.put("challenge_name", "challenge-document-link");
    body.put("challenge_tags", List.of());
    body.put("challenge_documents", documents);
    body.put("challenge_flags", List.of(Map.of("flag_type", "VALUE", "flag_value", "a flag")));
    return body;
  }

  private String saveDocument(String tenantId) {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setName("document-link-" + UUID.randomUUID());
    document.setTenant(new Tenant(tenantId));
    return documentRepository.save(document).getId();
  }

  private String saveChallengeInB() {
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    challenge.setTenant(new Tenant(tenantB));
    String id = challengeRepository.save(challenge).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  /**
   * Each document and the challenge carry the tenant the scenario relies on, then the ambient
   * tenant is reset to the default one the request thread of the header route carries.
   */
  private void armRequest(String challenge) {
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM documents WHERE document_id = ?",
                documentInB))
        .containsExactly(tenantB);
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM documents WHERE document_id = ?",
                documentInDefault))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    assertThat(
            column(
                entityManager,
                "SELECT tenant_id FROM challenges WHERE challenge_id = ?",
                challenge))
        .containsExactly(tenantB);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
