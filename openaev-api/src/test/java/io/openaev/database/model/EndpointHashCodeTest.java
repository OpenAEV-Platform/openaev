package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code Endpoint.injects} is a lazy {@code Set}: {@code PersistentSet.hashCode()} reads the whole
 * collection. A field-based hashCode therefore loaded every inject of the endpoint each time it was
 * put in a hash-based collection (scope resolution, dynamic asset groups), up to an OOM on an
 * endpoint targeted by many simulations.
 */
@Transactional
@DisplayName("Endpoint hashCode does not load its injects")
class EndpointHashCodeTest extends IntegrationTest {

  @Autowired private EntityManager entityManager;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;

  @BeforeEach
  void setUp() {
    endpointComposer.reset();
    injectComposer.reset();
  }

  @Test
  @DisplayName("hashCode and a HashSet insertion leave the lazy injects collection uninitialized")
  void given_endpointTargetedByInjects_should_notInitializeInjects_when_hashed() {
    // Arrange: an endpoint targeted by an inject, reloaded in a clean session
    EndpointComposer.Composer endpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
    injectComposer.forInject(InjectFixture.getDefaultInject()).withEndpoint(endpoint).persist();
    entityManager.flush();
    entityManager.clear();
    Endpoint reloaded = entityManager.find(Endpoint.class, endpoint.get().getId());

    // Act
    int hash = reloaded.hashCode();
    Set<Asset> assets = new HashSet<>();
    assets.add(reloaded);

    // Assert
    assertThat(Hibernate.isInitialized(reloaded.getInjects()))
        .as("hashing an endpoint must not load its injects")
        .isFalse();
    assertThat(hash).isEqualTo(reloaded.hashCode());
    assertThat(assets).contains(reloaded);
  }
}
