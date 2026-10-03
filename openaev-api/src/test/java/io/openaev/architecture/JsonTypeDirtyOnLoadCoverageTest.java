package io.openaev.architecture;

import static io.openaev.architecture.JsonTypeDirtyOnLoadCatalog.activeTables;
import static io.openaev.architecture.JsonTypeDirtyOnLoadCatalog.describe;
import static io.openaev.architecture.JsonTypeDirtyOnLoadCatalog.fieldsOnActiveTables;
import static io.openaev.architecture.JsonTypeDirtyOnLoadCatalog.fieldsOnInactiveTables;
import static io.openaev.architecture.JsonTypeDirtyOnLoadCatalog.jsonFieldsByTable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.architecture.JsonTypeDirtyOnLoadCatalog.JsonField;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Keeps the dirty-on-load family measured as the platform grows.
 *
 * <p>Whether a JSON-mapped field is dirty after every load depends on the value stored, not on the
 * mapping alone, so the verdict belongs to {@link JsonTypeDirtyOnLoadProbeTest}, which loads a real
 * row. What is decidable without a database is the INVENTORY: which fields exist, and which of them
 * are stored in a table the statement inspector gates. This class holds the inventory against two
 * lists, so the build fails rather than going quiet when:
 *
 * <ul>
 *   <li>a new JSON-mapped field lands on an entity whose table is already tenant-active, or a table
 *       that stores one becomes active, and no probe case measures it;
 *   <li>a new one lands on a table that is not active yet, which is latent rather than live and is
 *       recorded as such so the activation of that table has to look at it.
 * </ul>
 */
@DisplayName("every JSON-mapped field is measured or recorded as latent")
class JsonTypeDirtyOnLoadCoverageTest {

  /**
   * JSON-mapped fields stored only in tables that are NOT tenant-active yet. They carry the same
   * latent defect: nothing rewrites their flush today, so a dirty-on-load entity costs an extra
   * {@code UPDATE} and nothing else. Activating one of these tables is what turns that into a
   * failed read, so the activation of each table measures its fields then.
   *
   * <p>This list shrinks as tables activate; a field leaving it must appear in {@link
   * JsonTypeDirtyOnLoadProbeTest#PROBED_FIELDS} at the same time.
   */
  private static final Set<String> LATENT_UNTIL_THEIR_TABLE_ACTIVATES =
      Set.of(
          "BaseInjectExpectation.results",
          "TechnicalInjectExpectation.expectedSecurityPlatforms",
          "CatalogConnectorConfiguration.connectorConfigurationDefault",
          "Condition.keyTypes",
          "ConnectorInstanceConfiguration.value",
          "InjectDependency.injectDependencyCondition",
          "InjectStatus.payloadOutput",
          "Step.conditionKeyTypes",
          "Step.data",
          "Step.input",
          "Step.output",
          "Step.outputParser",
          "Scenario.autonomousConfig",
          "UserEvent.payload",
          "WorkflowScopeRule.snapshotEnd",
          "WorkflowScopeRule.snapshotStart",
          "WorkflowState.entries");

  @Nested
  @DisplayName("the inventory it is built on")
  class Inventory {

    @Test
    @DisplayName("given the entity model, when scanned, then it finds entities, tables and fields")
    void given_the_entity_model_should_find_json_fields() {
      Set<JsonField> fields = jsonFieldsByTable().keySet();

      // An empty scan would make every assertion below pass while measuring nothing. These numbers
      // are floors taken well under what the model holds, not exact counts: they fail a broken
      // scan,
      // not a growing model.
      assertFalse(JsonTypeDirtyOnLoadCatalog.mappedEntities().isEmpty(), "no mapped entity found");
      assertTrue(
          fields.size() >= 35,
          "the scan found only " + fields.size() + " JSON-mapped fields, which cannot be right");
      assertTrue(
          activeTables().size() >= 40,
          "only " + activeTables().size() + " active tables read from application.properties");
      assertTrue(
          fieldsOnActiveTables().size() >= 25,
          "only " + fieldsOnActiveTables().size() + " JSON-mapped fields on active tables");
    }
  }

  @Nested
  @DisplayName("what the two lists promise")
  class Coverage {

    @Test
    @DisplayName(
        "given a JSON field on an active table, when listed, then a probe case measures it")
    void given_a_json_field_on_an_active_table_should_be_probed() {
      Set<JsonField> onActiveTables = fieldsOnActiveTables();

      Set<String> probed = new TreeSet<>(JsonTypeDirtyOnLoadProbeTest.PROBED_FIELDS);
      Set<String> actual = new TreeSet<>();
      onActiveTables.forEach(field -> actual.add(field.key()));
      assertEquals(
          probed,
          actual,
          "every JSON-mapped field stored in a tenant-active table needs a case in"
              + " JsonTypeDirtyOnLoadProbeTest, and nothing else belongs in PROBED_FIELDS. With"
              + " tables: "
              + describe(onActiveTables));
    }

    @Test
    @DisplayName("given a JSON field on an inactive table, when listed, then it is recorded latent")
    void given_a_json_field_on_an_inactive_table_should_be_recorded_as_latent() {
      Set<JsonField> onInactiveTables = fieldsOnInactiveTables();

      Set<String> latent = new TreeSet<>(LATENT_UNTIL_THEIR_TABLE_ACTIVATES);
      Set<String> actual = new TreeSet<>();
      onInactiveTables.forEach(field -> actual.add(field.key()));
      assertEquals(
          latent,
          actual,
          "a JSON-mapped field on a table that is not tenant-active is latent, not live: record it in"
              + " LATENT_UNTIL_THEIR_TABLE_ACTIVATES, and move it to the probe when its table"
              + " activates. With tables: "
              + describe(onInactiveTables));
    }

    @Test
    @DisplayName("given the two lists, when compared, then no field is in both")
    void given_the_two_lists_should_not_overlap() {
      Set<String> both = new TreeSet<>(JsonTypeDirtyOnLoadProbeTest.PROBED_FIELDS);
      both.retainAll(LATENT_UNTIL_THEIR_TABLE_ACTIVATES);

      assertTrue(both.isEmpty(), "a field is both probed and recorded as latent: " + both);
    }
  }
}
