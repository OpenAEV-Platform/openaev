package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.raw.RawGrant;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.engine.api.CursorPageQuery;
import io.openaev.engine.model.snapshotobservation.EsVulnerabilityObservation;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves AC3 ("never emits a {@code from}, walks past the engine's result-window cap") by lowering
 * {@code engine.max-result-window} to 10 instead of indexing past 100,000 documents: a {@code
 * from}-based implementation fails once it would need to skip past position 10, while the keyset
 * implementation walks the whole set regardless of {@code max_result_window}.
 *
 * <p>A separate class because the lowered property requires its own Spring context (the component
 * template embedding {@code max_result_window} is written once, at driver construction).
 */
@SpringBootTest
@Transactional
@WithMockUser(isAdmin = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=BULK_SNAPSHOT_EXPORT",
      "engine.max-result-window=10"
    })
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("EngineService.searchCursorPaged - result window (AC3)")
class CursorPagedSearchResultWindowTest extends IntegrationTest {

  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;

  private static final Instant WINDOW_END = Instant.now().plus(1, ChronoUnit.DAYS);

  private static final RawUserAuth ADMIN_USER =
      new RawUserAuth() {
        @Override
        public String getUser_id() {
          return "test-admin-" + UUID.randomUUID();
        }

        @Override
        public boolean getUser_admin() {
          return true;
        }

        @Override
        public Set<RawGrant> getUser_grants() {
          return Set.of();
        }
      };

  @BeforeEach
  void resetIndex() throws IOException {
    endpointComposer.reset();
    findingComposer.reset();
    injectComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }
  }

  private void newGrain() {
    EndpointComposer.Composer endpointWrapper =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint("ep-" + UUID.randomUUID()));
    FindingComposer.Composer findingWrapper =
        findingComposer
            .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
            .withEndpoint(endpointWrapper);
    injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withFinding(findingWrapper)
        .persist();
  }

  private void indexAndWait() throws InterruptedException {
    entityManager.flush();
    entityManager.clear();
    engineService.bulkProcessing(engineContext.getModels().stream());
    Thread.sleep(1_000);
  }

  @Test
  @DisplayName(
      "paging past max_result_window=10 with size 5 walks the whole set (a `from`-based "
          + "implementation would fail with \"Result window is too large\")")
  void given_datasetLargerThanMaxResultWindow_should_pageThroughAllDocuments()
      throws InterruptedException {
    int total = 25;
    for (int i = 0; i < total; i++) {
      newGrain();
    }
    indexAndWait();

    Set<String> seen = new LinkedHashSet<>();
    CursorPageQuery.Keyset cursor = null;
    List<EsVulnerabilityObservation> lastPage;
    int guard = 0;
    do {
      CursorPageQuery query = new CursorPageQuery(null, cursor, WINDOW_END, 5);
      lastPage =
          engineService.searchCursorPaged(ADMIN_USER, EsVulnerabilityObservation.class, query);
      for (EsVulnerabilityObservation doc : lastPage) {
        assertThat(seen.add(doc.getBase_id())).as("no duplicate id across pages").isTrue();
      }
      if (!lastPage.isEmpty()) {
        EsVulnerabilityObservation last = lastPage.getLast();
        cursor = new CursorPageQuery.Keyset(last.getBase_updated_at(), last.getBase_id());
      }
      guard++;
    } while (!lastPage.isEmpty() && guard < 20);

    assertThat(seen).hasSize(total);
  }
}
