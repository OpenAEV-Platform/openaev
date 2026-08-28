package io.openaev.database.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("InjectRepository")
class InjectRepositoryTest extends IntegrationTest {

  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectRepository injectRepository;

  @Test
  @DisplayName(
      "updateUpdatedAt writes the inject timestamp through Hibernate (so the inspector covers it)")
  void updateUpdatedAt_writesTimestamp() {
    Inject inject = injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
    Instant target = Instant.parse("2020-01-02T03:04:05Z");
    entityManager.flush();

    int rows = injectRepository.updateUpdatedAt(inject.getId(), target);

    entityManager.clear();
    assertThat(rows).isEqualTo(1);
    Optional<Inject> updated = injectRepository.findById(inject.getId());
    assertThat(updated).isPresent();
    assertThat(updated.get().getUpdatedAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(target);
  }
}
