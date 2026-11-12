package io.openaev.service.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.config.MarkedTable;
import io.openaev.config.MarkedTables;
import io.openaev.config.MarkingDerivedTables;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.ManualInjectExpectation;
import io.openaev.database.model.Scenario;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.service.stream.StreamMarkingRules.EmbeddedRule;
import io.openaev.service.stream.StreamMarkingRules.ListingRule;
import io.openaev.service.stream.StreamMarkingRules.RowRule;
import jakarta.persistence.Entity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * The stream rules are read from the registry ({@link MarkingDerivedTables}) and the JPA mapping: a
 * table added to the registry is filtered on the stream too.
 */
@DisplayName("Stream marking rules derived from the registry and the JPA mapping")
class StreamMarkingRulesTest {

  private static final MarkedTables ASSETS_ACTIVE =
      new MarkedTables(Map.of("assets", new MarkedTable("assets")))
          .withDerived(MarkingDerivedTables.ALL);

  private static final List<Class<?>> ENTITIES = entities();

  private static StreamMarkingRules rules(MarkedTables markedTables) {
    return new StreamMarkingRules(markedTables, ENTITIES, ObjectMapperHelper.openAEVJsonMapper());
  }

  @Test
  @DisplayName("an asset carries its own markings")
  void given_markedTable_should_readTheMarkingsOfTheRow() {
    // Act
    StreamMarkingRules rules = rules(ASSETS_ACTIVE);

    // Assert
    assertThat(rules.forEntity(Endpoint.class).row())
        .isEqualTo(RowRule.ownMarkings("assets", "asset_markings"));
  }

  @Test
  @DisplayName("an entity on one parent row is hidden with it, through a chain if need be")
  void given_parentLink_should_followTheParentRow() {
    // Act
    StreamMarkingRules rules = rules(ASSETS_ACTIVE);

    // Assert
    assertThat(rules.forEntity(Agent.class).row())
        .isEqualTo(RowRule.parent("agents", "agent_asset", "assets"));
    assertThat(rules.forEntity(DetectionInjectExpectation.class).row())
        .isEqualTo(RowRule.parent("injects_expectations", "inject_expectation_asset", "assets"));
    assertThat(rules.forEntity(ExecutionTrace.class).row())
        .isEqualTo(RowRule.parent("execution_traces", "agent", "agents"));
    // A manual expectation shares the table but has no asset: like a NULL asset_id in SQL
    assertThat(rules.forEntity(ManualInjectExpectation.class).row())
        .isEqualTo(RowRule.parent("injects_expectations", null, "assets"));
  }

  @Test
  @DisplayName("an entity listing assets keeps the visible ones, and is hidden when about them")
  void given_listedAssets_should_beFilteredAndAboutThroughLinkRows() {
    // Act
    StreamMarkingRules rules = rules(ASSETS_ACTIVE);

    // Assert
    assertThat(rules.forEntity(Inject.class).listings())
        .containsExactly(new ListingRule("inject_assets", "assets"));
    assertThat(rules.forEntity(Inject.class).row()).isNull();
    assertThat(rules.forEntity(AssetGroup.class).listings())
        .containsExactlyInAnyOrder(
            new ListingRule("asset_group_assets", "assets"),
            new ListingRule("asset_group_dynamic_assets", "assets"));
    assertThat(rules.forEntity(Finding.class).listings())
        .containsExactly(new ListingRule("finding_assets", "assets"));
    assertThat(rules.forEntity(Finding.class).row())
        .isEqualTo(RowRule.about("findings", "finding_assets", "assets"));
  }

  @Test
  @DisplayName("objects of a derived table embedded whole are filtered, at any depth")
  void given_embeddedDerivedObjects_should_beFiltered() {
    // Act
    StreamMarkingRules rules = rules(ASSETS_ACTIVE);

    // Assert
    assertThat(rules.forEntity(Inject.class).embedded())
        .containsExactlyInAnyOrder(
            new EmbeddedRule(
                "inject_expectations", "inject_expectation_id", "injects_expectations"),
            new EmbeddedRule(
                "inject_status.status_traces", "execution_trace_id", "execution_traces"));
  }

  @Test
  @DisplayName("a table out of the registry, or no marked table at all, is not filtered")
  void given_tableOutOfTheRegistry_should_notBeFiltered() {
    // Arrange
    MarkedTables withoutTraces =
        new MarkedTables(Map.of("assets", new MarkedTable("assets")))
            .withDerived(
                MarkingDerivedTables.ALL.stream()
                    .filter(entry -> !entry.table().equals("execution_traces"))
                    .toList());

    // Act
    StreamMarkingRules rules = rules(withoutTraces);

    // Assert
    assertThat(rules.forEntity(ExecutionTrace.class).isEmpty()).isTrue();
    assertThat(rules.forEntity(Inject.class).embedded())
        .extracting(EmbeddedRule::path)
        .containsExactly("inject_expectations");
    assertThat(rules.forEntity(Scenario.class).isEmpty()).isTrue();
    assertThat(rules(MarkedTables.EMPTY).forEntity(Inject.class).isEmpty()).isTrue();
  }

  private static List<Class<?>> entities() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    return scanner.findCandidateComponents("io.openaev.database.model").stream()
        .<Class<?>>map(
            definition -> {
              try {
                return Class.forName(definition.getBeanClassName());
              } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }
}
