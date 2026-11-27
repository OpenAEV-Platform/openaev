package io.openaev.api.asset;

import static io.openaev.api.asset.AssetOptionsApi.TENANT_ASSET_URI;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.asset.dto.AssetLinkInput;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetLinkType;
import io.openaev.database.model.Endpoint;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Automatic LOGGED_ON links from the account an agent runs as (#9725). */
@Transactional
@WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,EXPLORATION_GRAPH")
@DisplayName("Agent session links")
class AgentSessionLinkServiceTest extends IntegrationTest {

  private static final String LINKS_URI =
      TENANT_ASSET_URI.replace("{tenantId}", DEFAULT_TENANT_UUID) + "/{assetId}/links";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentSessionLinkService sessions;
  @PersistenceContext private EntityManager entityManager;

  private JdbcTemplate jdbc;
  private Endpoint host;
  private Endpoint account;
  private String accountName;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    endpointComposer.reset();
    host = endpoint("session-host-" + UUID.randomUUID(), null);
    accountName = "svc-" + UUID.randomUUID();
    account = endpoint(accountName, AssetCategory.IDENTITY);
    entityManager.flush();
  }

  @Test
  @DisplayName(
      "given a reported session, should create the link with source AGENT, then refresh it")
  void given_reportedSession_should_createThenRefreshTheLink() {
    // Act
    Instant first = Instant.parse("2026-11-23T10:00:00Z");
    Instant second = Instant.parse("2026-11-23T11:00:00Z");
    String linked = sessions.recordSession(host, "CORP\\" + accountName.toUpperCase(), first);
    sessions.recordSession(host, accountName, second);

    // Assert
    assertThat(linked).isEqualTo(account.getId());
    entityManager.flush();
    assertThat(
            jdbc.queryForObject(
                "SELECT asset_link_source FROM assets_links WHERE asset_id = ?"
                    + " AND linked_asset_id = ? AND asset_link_type = 'LOGGED_ON'",
                String.class,
                account.getId(),
                host.getId()))
        .isEqualTo("AGENT");
    assertThat(
            jdbc.queryForObject(
                "SELECT asset_link_last_seen_at FROM assets_links WHERE asset_id = ?",
                java.sql.Timestamp.class,
                account.getId()))
        .isEqualTo(java.sql.Timestamp.from(second));
  }

  @Test
  @DisplayName("given a system account or no matching identity, should link nothing")
  void given_systemOrUnknownAccount_should_linkNothing() {
    assertThat(sessions.recordSession(host, "root", Instant.now())).isNull();
    assertThat(sessions.recordSession(host, "NT AUTHORITY\\SYSTEM", Instant.now())).isNull();
    assertThat(sessions.recordSession(host, "nobody-" + UUID.randomUUID(), Instant.now())).isNull();
    assertThat(storedLinks()).isZero();
  }

  @Test
  @DisplayName("given a person removed the automatic link, the next report should not restore it")
  void given_manualRemoval_should_winOverTheAgent() throws Exception {
    // Arrange
    sessions.recordSession(host, accountName, Instant.now());
    entityManager.flush();

    // Act
    mvc.perform(
            delete(
                    LINKS_URI + "/{linkedAssetId}/{linkType}",
                    account.getId(),
                    host.getId(),
                    "LOGGED_ON")
                .with(csrf()))
        .andExpect(status().isOk());
    String again = sessions.recordSession(host, accountName, Instant.now());

    // Assert
    assertThat(again).isNull();
    mvc.perform(get(LINKS_URI, host.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("given a manual link, the agent should refresh it without taking it over")
  void given_manualLink_should_keepItsSource() throws Exception {
    // Arrange
    mvc.perform(
            post(LINKS_URI, account.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(new AssetLinkInput(host.getId(), AssetLinkType.LOGGED_ON))))
        .andExpect(status().isOk());

    // Act
    sessions.recordSession(host, accountName, Instant.now());

    // Assert
    mvc.perform(get(LINKS_URI, host.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].asset_link_origin").value("MANUAL"));
  }

  @Test
  @DisplayName("account names: domain and UPN forms give the bare user, system accounts nothing")
  void accountNames() {
    assertThat(AgentSessionLinkService.accountNames("CORP\\Alice"))
        .contains("corp\\alice", "alice");
    assertThat(AgentSessionLinkService.accountNames("alice@corp.io")).contains("alice");
    assertThat(AgentSessionLinkService.accountNames("root")).isEmpty();
    assertThat(AgentSessionLinkService.accountNames("  ")).isEmpty();
  }

  private Endpoint endpoint(String name, AssetCategory category) {
    Endpoint endpoint = EndpointFixture.createEndpoint(name);
    endpoint.setName(name);
    if (category != null) {
      endpoint.setCategory(category);
    }
    return endpointComposer.forEndpoint(endpoint).persist().get();
  }

  private int storedLinks() {
    entityManager.flush();
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM assets_links WHERE linked_asset_id = ?",
            Integer.class,
            host.getId());
    return count == null ? 0 : count;
  }
}
