package io.openaev.rest.document;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The control for {@link PlayerDocumentsAnonymousRouteTest}: with {@code documents} NOT active, an
 * anonymous player page of a non-default tenant is refused exactly the same way when the path names
 * the default tenant, or names no tenant at all. The refusal comes from the simulation lookup,
 * which resolves through the ambient tenant and belongs to the v1 layer {@code exercises} is still
 * on, not from the document scope. Activating {@code documents} therefore extends a gap that is
 * already there, it does not open one.
 *
 * <p>Not {@code @Transactional}: the rows are committed through an auto-committing {@link
 * JdbcTemplate} and removed on teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=challenges")
@DisplayName("Anonymous player pages of a non-default tenant, documents NOT active")
class PlayerPagesAnonymousRouteBeforeDocumentsTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String SIMULATION_DOCUMENTS =
      "/api/tenants/{tenantId}/player/simulations/{simulationId}/documents";
  private static final String LEGACY_SIMULATION_DOCUMENTS =
      "/api/player/simulations/{simulationId}/documents";
  private static final String PLAYER_DOCUMENTS =
      "/api/tenants/{tenantId}/player/{exerciseOrScenarioId}/documents";
  private static final String LEGACY_PLAYER_DOCUMENTS =
      "/api/player/{exerciseOrScenarioId}/documents";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String[]> seededRows = new ArrayList<>();

  private String exerciseId;
  private String rawToken;

  @BeforeEach
  void seedASimulationInANonDefaultTenant() {
    jdbc = new JdbcTemplate(dataSource);
    String tenantB = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantB,
        "player-before-" + tenantB);
    seededRows.add(new String[] {"tenants", "tenant_id", tenantB});
    exerciseId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        exerciseId,
        "player-before-" + exerciseId,
        tenantB);
    seededRows.add(new String[] {"exercises", "exercise_id", exerciseId});
    String playerId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        playerId,
        "player-before-" + playerId + "@openaev.io");
    seededRows.add(new String[] {"users", "user_id", playerId});
    rawToken = "player-before-token-" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO url_access_token (id, token_hash, url, user_id, exercise_id, expires_at)"
            + " VALUES (?, ?, ?, ?, ?, now() + interval '30 days')",
        UUID.randomUUID().toString(),
        sha256Hex(rawToken),
        "/api/player/simulations/" + exerciseId + "/documents",
        playerId,
        exerciseId);
    seededRows.add(new String[] {"url_access_token", "user_id", playerId});
    TenantContext.clearCurrentTenant();
    assertEquals(
        DEFAULT_TENANT,
        TenantContext.getCurrentTenant(),
        "the ambient tenant must be the default one, as it is outside a tenant-prefixed request");
  }

  @AfterEach
  void cleanup() {
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName(
      "given documents are not active, when a simulation of tenant B is asked for under the default tenant, then the page is refused as it is once documents activate")
  void given_documentsNotActive_should_refuseTheDefaultTenantPathAllTheSame() throws Exception {
    // -- ACT & ASSERT --
    asPlayer(get(SIMULATION_DOCUMENTS, DEFAULT_TENANT, exerciseId))
        .andExpect(status().isBadRequest());
    asPlayer(get(PLAYER_DOCUMENTS, DEFAULT_TENANT, exerciseId)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "given documents are not active, when a simulation of tenant B is asked for with no tenant named, then the page is refused as it is once documents activate")
  void given_documentsNotActive_should_refuseTheNonPrefixedRouteAllTheSame() throws Exception {
    // -- ACT & ASSERT --
    asPlayer(get(LEGACY_SIMULATION_DOCUMENTS, exerciseId)).andExpect(status().isBadRequest());
    asPlayer(get(LEGACY_PLAYER_DOCUMENTS, exerciseId)).andExpect(status().isNotFound());
  }

  private org.springframework.test.web.servlet.ResultActions asPlayer(
      MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.cookie(new Cookie(URL_ACCESS_COOKIE_NAME, rawToken)));
  }

  private static String sha256Hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
