package io.openaev.database.audit;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.utils.fixtures.EndpointFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * An entity subclass inherits the listeners of its superclass: Hibernate runs a listener once per
 * declaration in the hierarchy, so one repeated on the subclass publishes every event twice (an
 * endpoint streamed twice, #9717).
 */
@RecordApplicationEvents
@DisplayName("The model listener of an entity subclass runs once")
class ModelBaseListenerSubclassTest extends IntegrationTest {

  @Autowired private EndpointRepository endpointRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ApplicationEvents applicationEvents;

  private final List<String> seededEndpoints = new ArrayList<>();

  @BeforeEach
  void setUp() {
    applicationEvents.clear();
  }

  @AfterEach
  void tearDown() {
    inScope(() -> endpointRepository.deleteAllById(seededEndpoints));
    seededEndpoints.clear();
  }

  @Test
  @DisplayName("saving an endpoint publishes one event")
  void given_newEndpoint_should_publishOneEvent() {
    // Arrange
    Endpoint endpoint = newEndpoint();

    // Act
    inScope(() -> endpointRepository.save(endpoint));
    seededEndpoints.add(endpoint.getId());

    // Assert
    assertThat(baseEventsOn(endpoint.getId(), DATA_PERSIST)).isEqualTo(1);
  }

  @Test
  @DisplayName("deleting an endpoint publishes one event and one index event")
  void given_deletedEndpoint_should_publishOneEventAndOneIndexEvent() {
    // Arrange
    Endpoint endpoint = newEndpoint();
    inScope(() -> endpointRepository.save(endpoint));
    applicationEvents.clear();

    // Act
    inScope(() -> endpointRepository.deleteById(endpoint.getId()));

    // Assert
    assertThat(baseEventsOn(endpoint.getId(), DATA_DELETE)).isEqualTo(1);
    assertThat(
            applicationEvents.stream(IndexEvent.class)
                .filter(event -> endpoint.getId().equals(event.getId()))
                .count())
        .isEqualTo(1);
  }

  private static Endpoint newEndpoint() {
    Endpoint endpoint = EndpointFixture.createEndpoint("listener-" + UUID.randomUUID());
    endpoint.setTenant(new Tenant(DEFAULT_TENANT_UUID));
    return endpoint;
  }

  private long baseEventsOn(String id, String type) {
    return applicationEvents.stream(BaseEvent.class)
        .filter(event -> type.equals(event.getType()))
        .filter(event -> event.getInstance() != null && id.equals(event.getInstance().getId()))
        .count();
  }

  private void inScope(Runnable work) {
    tenantTx.execute(
        TxCtx.forTenant(DEFAULT_TENANT_UUID),
        () -> {
          work.run();
          return null;
        });
  }
}
