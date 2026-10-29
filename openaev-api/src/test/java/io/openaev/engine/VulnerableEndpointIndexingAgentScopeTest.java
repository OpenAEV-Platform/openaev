package io.openaev.engine;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.engine.model.vulnerableendpoint.EsVulnerableEndpoint;
import io.openaev.engine.model.vulnerableendpoint.VulnerableEndpointHandler;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The vulnerable-endpoint indexing query reaches {@code agents} through three correlated
 * sub-queries that denormalize the agent ids, statuses and privileges onto each document. {@link
 * io.openaev.config.TenantStatementInspectorTest} pins the rewrite those sub-queries get; this
 * class is the other half, on the real stack: PostgreSQL has to accept the rewritten statement and
 * the arrays have to come back under the scope the sweep actually runs with.
 *
 * <p>The default test profile activates no table, so the existing indexing tests run with the
 * inspector inert and would not have noticed either a refusal or an array silently emptied by the
 * predicate. {@code agents} is armed here and armed alone, so what the assertions below measure can
 * only come from the table under test.
 *
 * <p>{@code EngineSyncExecutionJob} opens the sweep with {@code TxCtx.allTenants()}, which the
 * primitive resolves into the explicit list of tenant ids. Setting that list on the current
 * transaction is what {@link #setScope} does, exactly as {@code IndexingTenantScopeRegressionTest}
 * does for {@code collectors}.
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "openaev.tenant.active-tables=agents")
@DisplayName("vulnerable-endpoint indexing with agents v2-active")
class VulnerableEndpointIndexingAgentScopeTest extends IntegrationTest {

  /** The {@code :from} cursor: one hour ago, so everything composed below is newer. */
  private static final Instant FROM = Instant.now().minus(1, ChronoUnit.HOURS);

  @Autowired private VulnerableEndpointHandler vulnerableEndpointHandler;
  @Autowired private JdbcTemplate jdbc;

  @Autowired private EndpointComposer endpointComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;

  private String endpointId;
  private String agentId;

  @BeforeEach
  void composeOneVulnerableEndpointCarryingOneAgent() {
    endpointComposer.reset();
    exerciseComposer.reset();
    findingComposer.reset();
    injectComposer.reset();

    EndpointComposer.Composer endpointWrapper =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
    FindingComposer.Composer findingWrapper =
        findingComposer
            .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
            .withEndpoint(endpointWrapper);
    InjectComposer.Composer injectWrapper =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withEndpoint(endpointWrapper)
            .withFinding(findingWrapper);
    exerciseComposer
        .forExercise(ExerciseFixture.createDefaultExercise())
        .withInject(injectWrapper)
        .persist();
    entityManager.flush();

    endpointId = endpointWrapper.get().getId();
    // Written through JDBC: the inspector never rewrites it, so the row's tenant is what this test
    // says it is rather than whatever the ambient scope would have stamped.
    agentId = insertActiveAgent(endpointId, DEFAULT_TENANT_UUID);
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("given the resolved all-tenant sweep should denormalize the endpoint's own agent")
  void given_resolvedAllTenantSweep_should_denormalizeTheEndpointsOwnAgent() {
    // Arrange: the scope the sweep runs with, an explicit list of every tenant.
    setScope(everyTenantId());

    // Act: the whole point of running this on a stack is that the rewritten statement has to
    // parse, plan and execute. A shape PostgreSQL refuses fails here rather than in production.
    EsVulnerableEndpoint document = fetchDocument();

    // Assert
    assertThat(document.getBase_agents_side())
        .as("the endpoint's own agent must be denormalized onto its document")
        .contains(agentId);
    assertThat(document.getVulnerable_endpoint_agents_privileges())
        .as("the privileges array comes from a second sub-query on the same table")
        .hasSize(1);
    assertThat(document.getVulnerable_endpoint_agents_active_status())
        .as("the statuses array comes from a third sub-query on the same table")
        .containsExactly(true);
  }

  @Test
  @DisplayName("given another endpoint's agent should leave it off this endpoint's document")
  void given_anotherEndpointsAgent_should_leaveItOffThisEndpointsDocument() {
    // Arrange: a second endpoint in the same tenant, carrying its own agent. The tenant predicate
    // admits it; only the correlation to the outer asset keeps it out.
    String otherEndpointId = insertEndpoint(DEFAULT_TENANT_UUID);
    String otherAgentId = insertActiveAgent(otherEndpointId, DEFAULT_TENANT_UUID);
    setScope(everyTenantId());

    // Act
    EsVulnerableEndpoint document = fetchDocument();

    // Assert: positive first, so an empty array cannot satisfy the negative on its own.
    assertThat(document.getBase_agents_side()).contains(agentId);
    assertThat(document.getBase_agents_side())
        .as("a rewrite that loses the correlation turns the array into every agent in scope")
        .doesNotContain(otherAgentId);
    // The statuses and privileges arrays come from two more sub-queries on the same table and are
    // positional, not keyed: a correlation lost in either of them is invisible in the ids array
    // above, and shifts every status onto the wrong agent.
    assertThat(document.getVulnerable_endpoint_agents_active_status())
        .as("the statuses array must hold this endpoint's single agent, not the other one's too")
        .hasSize(1);
    assertThat(document.getVulnerable_endpoint_agents_privileges()).hasSize(1);
  }

  @Test
  @DisplayName("given a scope-less sweep should index the document with no agent at all")
  void given_scopeLessSweep_should_indexTheDocumentWithNoAgentAtAll() {
    // Arrange: no scope, which is what a background entry point that forgets one leaves behind.
    // can_access_tenant is fail-closed, so the three sub-queries read nothing. This is the shape
    // the collectors incident had, and the measurement that the predicate is really in the
    // statement: with agents disarmed the arrays come back full here.
    setScope("");

    // Act
    EsVulnerableEndpoint document = fetchDocument();

    // Assert: the document itself is still built (assets and findings are not armed here), only
    // its agent denormalization is empty.
    assertThat(document.getVulnerable_endpoint_id()).isEqualTo(endpointId);
    assertThat(document.getBase_agents_side())
        .as("with no scope the agent sub-queries must read nothing, not another tenant's rows")
        .isEmpty();
    assertThat(document.getVulnerable_endpoint_agents_privileges()).isNullOrEmpty();
  }

  // -- HELPERS --

  private EsVulnerableEndpoint fetchDocument() {
    return vulnerableEndpointHandler.fetch(FROM, 5000).stream()
        .filter(document -> endpointId.equals(document.getVulnerable_endpoint_id()))
        .findFirst()
        .orElseThrow();
  }

  /** Sets the transaction-local scope, as the primitive does once it has resolved the intention. */
  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  /** What {@code allTenants()} resolves to: every tenant id on the platform, comma separated. */
  private String everyTenantId() {
    return String.join(",", jdbc.queryForList("SELECT tenant_id FROM tenants", String.class));
  }

  private String insertEndpoint(String tenantId) {
    String id = UUID.randomUUID().toString();
    String name = "vuln-endpoint-indexing-" + id;
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at,"
            + " asset_updated_at, tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        id,
        name,
        tenantId,
        name);
    return id;
  }

  private String insertActiveAgent(String assetId, String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO agents (agent_id, agent_asset, agent_privilege, agent_deployment_mode,"
            + " agent_executed_by_user, agent_status, agent_last_seen, agent_created_at,"
            + " agent_updated_at, tenant_id)"
            + " VALUES (?, ?, 'admin', 'service', 'root', 'ACTIVE', now(), now(), now(), ?)",
        id,
        assetId,
        tenantId);
    return id;
  }
}
