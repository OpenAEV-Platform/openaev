package io.openaev.service.stream;

import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("The stream reads the markings of the rows an event points to")
class StreamVisibilityServiceReadTest extends IntegrationTest {

  @Autowired private StreamVisibilityService streamVisibilityService;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("given marked and unmarked assets should read their markings, through an agent too")
  void given_assetsAndAgents_should_readTheirMarkings() {
    // Arrange
    String tenantId = TenantContext.getCurrentTenant();
    AgentComposer.Composer agent = agentComposer.forAgent(AgentFixture.createDefaultAgentService());
    String marked =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(agent)
            .persist()
            .get()
            .getId();
    String unmarked =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get().getId();
    MarkingDefinition red =
        markingDefinitionComposer
            .forMarkingDefinition(createMarkingDefinition("TLP", uniqueDefinition(), 60, "#c62828"))
            .withTenantId(tenantId)
            .persist()
            .get();
    entityManager.flush();
    // Out of band, as in AssetMarkingIsolationTest: an ORM write of a marking the test user does
    // not hold is refused by the marking filter.
    jdbcTemplate.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        (Object) new String[] {red.getId()},
        marked);
    entityManager.clear();

    // Act: at system clearance, as the stream reads after commit
    Map<String, Map<String, Set<String>>> markings =
        tenantScopedTransaction.withSystemClearance(
            TxCtx.forTenant(tenantId),
            () ->
                streamVisibilityService.readMarkings(
                    Map.of(
                        "assets", Set.of(marked, unmarked, "unknown-asset"),
                        "agents", Set.of(agent.get().getId()))));

    // Assert
    assertThat(markings.get("assets"))
        .containsOnly(Map.entry(marked, Set.of(red.getId())), Map.entry(unmarked, Set.of()));
    assertThat(markings.get("agents"))
        .containsOnly(Map.entry(agent.get().getId(), Set.of(red.getId())));
  }
}
