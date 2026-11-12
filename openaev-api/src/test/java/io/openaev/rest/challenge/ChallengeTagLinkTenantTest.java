package io.openaev.rest.challenge;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.challenge.ChallengeApi.CHALLENGE_URI;
import static io.openaev.utils.TagLinkJdbc.column;
import static io.openaev.utils.TagLinkJdbc.saveTag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ChallengeFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * The challenge update links the challenge to the tags of the challenge's own tenant.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants. The tag ids of the
 * body resolve in the tenant of the challenge alone. The links are read from {@code
 * challenges_tags} through JDBC on the test connection, which the statement inspector does not
 * rewrite. The class arms the production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Challenge update links only the tags of the challenge tenant")
class ChallengeTagLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String CHALLENGES_TAGS =
      "SELECT tag_id FROM challenges_tags WHERE challenge_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TagRepository tagRepository;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String tagInB;
  private String tagInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("challenge-tag-link-b").getId();
    tagInB = saveTag(tagRepository, tenantB);
    tagInDefault = saveTag(tagRepository, Tenant.DEFAULT_TENANT_UUID);
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
        "given a challenge of B when the body names a tag of each tenant then only the tag of B is"
            + " linked")
    void given_challengeOfB_should_linkOnlyTheTagOfB() throws Exception {
      // -- Arrange --
      String challenge = saveChallengeInB(null);
      armRequest(challenge);

      // -- Act --
      putOnHeaderRoute(challenge, challengeBody(List.of(tagInB, tagInDefault)))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CHALLENGES_TAGS, challenge))
          .as("tags linked to the challenge of B: kept %s, dropped %s", tagInB, tagInDefault)
          .containsExactly(tagInB);
    }
  }

  @Nested
  @DisplayName("Body with a null tag list")
  class BodyWithNullTagList {

    @Test
    @DisplayName(
        "given a challenge update with a null tag list on a challenge of B holding a tag of B then"
            + " no tag stays linked")
    void given_challengeUpdateWithNullTagList_should_clearTheLinks() throws Exception {
      // -- Arrange --
      String challenge = saveChallengeInB(tagInB);
      armRequest(challenge);

      // -- Act --
      putOnHeaderRoute(challenge, challengeBody(null)).andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CHALLENGES_TAGS, challenge)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a challenge of B when the body under the path of B names a tag of B then the tag is"
            + " linked")
    void given_challengeOfB_should_linkTheTagOfBOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      String challenge = saveChallengeInB(null);
      armRequest(challenge);

      // -- Act --
      mvc.perform(
              put(TENANT_PREFIX + "/challenges/{id}", tenantB, challenge)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(challengeBody(List.of(tagInB))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CHALLENGES_TAGS, challenge)).containsExactly(tagInB);
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

  private Map<String, Object> challengeBody(List<String> tags) {
    Map<String, Object> body = new HashMap<>();
    body.put("challenge_name", "challenge-tag-link");
    body.put("challenge_tags", tags);
    body.put("challenge_documents", List.of());
    body.put("challenge_flags", List.of(Map.of("flag_type", "VALUE", "flag_value", "a flag")));
    return body;
  }

  private String saveChallengeInB(String tagId) {
    Challenge challenge = ChallengeFixture.createDefaultChallenge();
    challenge.setTenant(new Tenant(tenantB));
    if (tagId != null) {
      challenge.setTags(
          new HashSet<>(tagRepository.findAllByIdInAndTenantId(List.of(tagId), tenantB)));
    }
    String id = challengeRepository.save(challenge).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  /**
   * Each tag and the challenge carry the tenant the scenario relies on, then the ambient tenant is
   * reset to the default one the request thread of the header route carries.
   */
  private void armRequest(String challenge) {
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInB))
        .containsExactly(tenantB);
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInDefault))
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
