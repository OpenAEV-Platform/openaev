package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Injector;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.database.repository.InjectorRepository;
import io.opentelemetry.api.common.Attributes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryMetricCollector")
class InventoryMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;
  @Mock private InjectorRepository injectorRepository;
  @Mock private CollectorRepository collectorRepository;
  @Mock private ExecutorRepository executorRepository;
  @Mock private ConnectorInstanceRepository connectorInstanceRepository;

  @InjectMocks private InventoryMetricCollector inventoryMetricCollector;

  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> supplierCaptor;

  private static Injector injector(String id, String type) {
    Injector injector = new Injector();
    injector.setId(id);
    injector.setName(type);
    injector.setType(type);
    return injector;
  }

  private static ConnectorInstancePersisted catalogInstance(
      String slug, String idKey, String connectorId) {
    return catalogInstance(slug, idKey, JsonNodeFactory.instance.textNode(connectorId));
  }

  private static ConnectorInstancePersisted catalogInstance(
      String slug, String idKey, JsonNode connectorIdValue) {
    CatalogConnector catalogConnector = new CatalogConnector();
    catalogConnector.setSlug(slug);
    ConnectorInstanceConfiguration configuration = new ConnectorInstanceConfiguration();
    configuration.setKey(idKey);
    configuration.setValue(connectorIdValue);
    ConnectorInstancePersisted instance = new ConnectorInstancePersisted();
    instance.setCatalogConnector(catalogConnector);
    instance.setConfigurations(Set.of(configuration));
    return instance;
  }

  private Supplier<Map<Attributes, Long>> capturedInjectorSupplier() {
    inventoryMetricCollector.init();
    verify(metricRegistry)
        .registerMultiGauge(eq("injectors_deployed_by_identity"), any(), supplierCaptor.capture());
    verify(metricRegistry).registerMultiGauge(eq("collectors_deployed_by_identity"), any(), any());
    verify(metricRegistry).registerMultiGauge(eq("executors_deployed_by_identity"), any(), any());
    return supplierCaptor.getValue();
  }

  @Test
  @DisplayName("given a catalog-deployed injector should report the catalog slug as managed")
  void given_catalogDeployedInjector_should_reportCatalogSlugAsManaged() {
    // Arrange
    when(injectorRepository.findAll()).thenReturn(List.of(injector("injector-1", "openaev_email")));
    when(connectorInstanceRepository.findAllByCatalogConnectorContainerType(ConnectorType.INJECTOR))
        .thenReturn(
            List.of(
                catalogInstance(
                    "Email-Injector", ConnectorType.INJECTOR.getIdKeyName(), "injector-1")));

    // Act
    Map<Attributes, Long> inventory = capturedInjectorSupplier().get();

    // Assert
    assertThat(inventory)
        .containsExactly(
            Map.entry(
                Attributes.of(
                    stringKey("slug"), "email-injector",
                    booleanKey("managed"), true,
                    stringKey("type"), "openaev_email"),
                1L));
  }

  @Test
  @DisplayName("given a manually deployed injector should fall back to its type slug as unmanaged")
  void given_manuallyDeployedInjector_should_fallBackToTypeSlugAsUnmanaged() {
    // Arrange
    when(injectorRepository.findAll())
        .thenReturn(List.of(injector("injector-1", "openaev_caldera")));
    when(connectorInstanceRepository.findAllByCatalogConnectorContainerType(ConnectorType.INJECTOR))
        .thenReturn(List.of());

    // Act
    Map<Attributes, Long> inventory = capturedInjectorSupplier().get();

    // Assert
    assertThat(inventory)
        .containsExactly(
            Map.entry(
                Attributes.of(
                    stringKey("slug"), "openaev_caldera",
                    booleanKey("managed"), false,
                    stringKey("type"), "openaev_caldera"),
                1L));
  }

  @Test
  @DisplayName("given identical identities should aggregate counts per attribute set")
  void given_identicalIdentities_should_aggregateCountsPerAttributeSet() {
    // Arrange
    when(injectorRepository.findAll())
        .thenReturn(
            List.of(
                injector("injector-1", "openaev_http"), injector("injector-2", "openaev_http")));
    when(connectorInstanceRepository.findAllByCatalogConnectorContainerType(ConnectorType.INJECTOR))
        .thenReturn(List.of());

    // Act
    Map<Attributes, Long> inventory = capturedInjectorSupplier().get();

    // Assert
    assertThat(inventory).hasSize(1);
    assertThat(inventory.values()).containsExactly(2L);
  }

  @Test
  @DisplayName("given a blank type should skip the component even when catalog-managed")
  void given_blankType_should_skipComponentEvenWhenManaged() {
    // Arrange
    when(injectorRepository.findAll())
        .thenReturn(List.of(injector("injector-1", " "), injector("injector-2", null)));
    when(connectorInstanceRepository.findAllByCatalogConnectorContainerType(ConnectorType.INJECTOR))
        .thenReturn(
            List.of(
                catalogInstance(
                    "Email-Injector", ConnectorType.INJECTOR.getIdKeyName(), "injector-1")));

    // Act
    Map<Attributes, Long> inventory = capturedInjectorSupplier().get();

    // Assert
    assertThat(inventory).isEmpty();
  }

  @Test
  @DisplayName("given a catalog row without slug should skip the managed component")
  void given_catalogRowWithoutSlug_should_skipManagedComponent() {
    // Arrange
    when(injectorRepository.findAll()).thenReturn(List.of(injector("injector-1", "openaev_email")));
    when(connectorInstanceRepository.findAllByCatalogConnectorContainerType(ConnectorType.INJECTOR))
        .thenReturn(
            List.of(catalogInstance(null, ConnectorType.INJECTOR.getIdKeyName(), "injector-1")));

    // Act
    Map<Attributes, Long> inventory = capturedInjectorSupplier().get();

    // Assert
    assertThat(inventory).isEmpty();
  }

  @Test
  @DisplayName("given a type with surrounding whitespace should trim it for both type and slug")
  void given_typeWithWhitespace_should_trimTypeAndSlug() {
    // Arrange
    when(injectorRepository.findAll())
        .thenReturn(List.of(injector("injector-1", "  OpenAEV_Email  ")));
    when(connectorInstanceRepository.findAllByCatalogConnectorContainerType(ConnectorType.INJECTOR))
        .thenReturn(List.of());

    // Act
    Map<Attributes, Long> inventory = capturedInjectorSupplier().get();

    // Assert
    assertThat(inventory)
        .containsExactly(
            Map.entry(
                Attributes.of(
                    stringKey("slug"), "openaev_email",
                    booleanKey("managed"), false,
                    stringKey("type"), "OpenAEV_Email"),
                1L));
  }

  @Test
  @DisplayName(
      "given a non-textual connector id configuration should classify the component as manual")
  void given_nonTextualConnectorIdConfiguration_should_classifyAsManual() {
    // Arrange: a JSON null id value must not become the literal string "null"
    when(injectorRepository.findAll()).thenReturn(List.of(injector("injector-1", "openaev_email")));
    when(connectorInstanceRepository.findAllByCatalogConnectorContainerType(ConnectorType.INJECTOR))
        .thenReturn(
            List.of(
                catalogInstance(
                    "Email-Injector",
                    ConnectorType.INJECTOR.getIdKeyName(),
                    JsonNodeFactory.instance.nullNode())));

    // Act
    Map<Attributes, Long> inventory = capturedInjectorSupplier().get();

    // Assert
    assertThat(inventory)
        .containsExactly(
            Map.entry(
                Attributes.of(
                    stringKey("slug"), "openaev_email",
                    booleanKey("managed"), false,
                    stringKey("type"), "openaev_email"),
                1L));
  }

  @Test
  @DisplayName("given a repository failure should return an empty inventory instead of throwing")
  void given_repositoryFailure_should_returnEmptyInventory() {
    // Arrange
    when(injectorRepository.findAll()).thenThrow(new IllegalStateException("database is down"));

    // Act
    Map<Attributes, Long> inventory = capturedInjectorSupplier().get();

    // Assert
    assertThat(inventory).isEmpty();
  }
}
