package io.openaev.database.model;

import static io.openaev.utils.fixtures.PayloadFixture.createAiAttack;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.PayloadRepository;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hibernate snapshots a {@code @Type(JsonType.class)} attribute when it is written and compares the
 * snapshot with the current value at every flush; Hypersistence compares maps and collections with
 * {@code equals}. A snapshot that is not an exact copy (a {@code Long} read back as an {@code
 * Integer} after a JSON round trip) makes the row dirty forever: an UPDATE, and the listener chain
 * (index, audit, stream), on every flush of the session.
 */
@Transactional
@DisplayName("Unchanged JSON attributes are not rewritten")
class JsonAttributeDirtyCheckTest extends IntegrationTest {

  @Autowired private EntityManager entityManager;
  @Autowired private PayloadRepository payloadRepository;

  @Test
  @DisplayName("A Long in a Map<String, Object> JSON attribute does not make the row dirty")
  void given_unchangedMapHoldingLong_should_notUpdateOnNextFlush() {
    // Arrange: a managed entity whose JSON map is changed in memory with a Long, then written once
    AiAttack aiAttack = createAiAttack("say hello");
    aiAttack.setTenant(entityManager.getReference(Tenant.class, Tenant.DEFAULT_TENANT_UUID));
    AiAttack managed = payloadRepository.save(aiAttack);
    entityManager.flush();
    managed.setMultiTurn(new HashMap<>(Map.of("turns", 3L, "mode", "chat")));
    entityManager.flush();
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();

    // Act: nothing changed since the last write
    entityManager.flush();

    // Assert
    assertThat(stats.getEntityUpdateCount())
        .as("an unchanged JSON attribute must not be written again")
        .isZero();
  }
}
