package io.openaev.scheduler.jobs.engine_sync;

import static io.openaev.scheduler.jobs.engine_sync.EngineSyncExecutionJob.ENGINE_CONTEXT_INSTANCE_KEY;
import static io.openaev.scheduler.jobs.engine_sync.EngineSyncExecutionJob.ENGINE_SERVICE_INSTANCE_KEY;
import static io.openaev.scheduler.jobs.engine_sync.EngineSyncExecutionJob.MODEL_NAME_KEY;
import static io.openaev.scheduler.jobs.engine_sync.EngineSyncExecutionJob.TENANT_TRANSACTION_INSTANCE_KEY;
import static io.openaev.utils.SensitiveValueMaskingUtils.MASKING_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Tenant;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.Handler;
import io.openaev.engine.api.CursorPageQuery;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.model.finding.EsFinding;
import io.openaev.engine.model.finding.FindingHandler;
import io.openaev.migration.V6_20261015163917000__Request_finding_refeed_after_sensitive_value_masking;
import io.openaev.service.EsIndexingUtils;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.awaitility.Awaitility;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The upgrade path of index-time masking, end to end: documents written in cleartext by an instance
 * that did not mask are masked in place by the re-feed the migration requests, through the real
 * engine sync, without the index ever being wiped.
 *
 * <p>Deliberately NOT {@code @Transactional}, unlike the convention: the engine sync opens its own
 * transaction through {@link TenantScopedTransaction}, which refuses to run inside an active one.
 * Rows are committed by the composers and the migration, and removed by auto-committed JDBC.
 *
 * <p>Findings are tenant-active here, as in production: in v2, a read without a tenant scope
 * silently returns nothing, so a re-feed reading without scope would rewrite nothing - which a test
 * with findings on v1 would not see.
 *
 * <p>Engine-agnostic (es8, es9 and OpenSearch run it): documents are only written and read through
 * the {@link EngineService} facade.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "openaev.tenant.active-tables=findings,assets,asset_groups")
@DisplayName("Finding re-feed after index-time masking, end to end")
class FindingRefeedEndToEndTest extends IntegrationTest {

  private static final String FINDING_MODEL = "finding";
  private static final String CLEARTEXT_CREDENTIALS =
      FindingFixture.CREDENTIALS_USERNAME + ":" + FindingFixture.CREDENTIALS_PASSWORD;
  private static final String MASKED_CREDENTIALS = "admin:mo******";

  @Autowired
  private V6_20261015163917000__Request_finding_refeed_after_sensitive_value_masking migration;

  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private FindingHandler findingHandler;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectComposer injectComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private Instant arrangeStart;
  private final List<String> injectIds = new ArrayList<>();
  private final List<String> documentIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    injectComposer.reset();
    findingComposer.reset();
    // Before the findings' backdated update time (see persistFinding), at millisecond precision as
    // the engine compares dates
    arrangeStart = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
  }

  @AfterEach
  void tearDown() {
    for (String injectId : injectIds) {
      // Cascades to the findings
      jdbc.update("DELETE FROM injects WHERE inject_id = ?", injectId);
    }
    if (!documentIds.isEmpty()) {
      engineService.bulkDelete(documentIds);
    }
    injectIds.clear();
    documentIds.clear();
    // The migration registered the re-feed, and the ready event that drains it has already fired
    EsIndexingUtils.drainRefeedRequests();
  }

  // -- Arrange helpers --

  private String persistFinding(Finding finding) {
    FindingComposer.Composer findingWrapper = findingComposer.forFinding(finding);
    InjectComposer.Composer injectWrapper =
        injectComposer.forInject(InjectFixture.getDefaultInject()).withFinding(findingWrapper);
    // One scoped transaction: saved apart, the finding saved by the inject's cascade would be
    // merged by a scope-less read of the tenant-active table, which sees nothing.
    tenantTx.execute(TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), () -> injectWrapper.persist());
    injectIds.add(injectWrapper.get().getId());
    String findingId = findingWrapper.get().getId();
    documentIds.add(findingId);
    // Older than the grace window: a regular sync round re-fetches the rows updated within it, and
    // would then rewrite the document whatever the migration does. Only a cursor moved back to
    // epoch reaches a row this old again.
    jdbc.update(
        "UPDATE findings SET finding_updated_at = now() - interval '1 hour' WHERE finding_id = ?",
        findingId);
    return findingId;
  }

  private void moveFindingCursorTo(Instant cursor) {
    jdbc.update(
        "INSERT INTO indexing_status"
            + " (indexing_status_type, indexing_status_indexing_date, indexing_status_last_id)"
            + " VALUES (?, ?, NULL)"
            + " ON CONFLICT (indexing_status_type) DO UPDATE"
            + " SET indexing_status_indexing_date = EXCLUDED.indexing_status_indexing_date,"
            + " indexing_status_last_id = NULL",
        FINDING_MODEL,
        Timestamp.from(cursor));
  }

  /**
   * Writes the documents of the given findings the way an instance that did not mask yet would: the
   * real document, with the value in cleartext and no masking version. Also writes a ghost: a copy
   * of the credentials document under an id that never existed in PostgreSQL.
   *
   * @return the id of the ghost document
   */
  private String indexAsOlderInstance(String credentialsFindingId) {
    String ghostId = UUID.randomUUID().toString();
    documentIds.add(ghostId);
    Handler<EsFinding> olderInstance =
        (from, limit) -> {
          List<EsFinding> documents = new ArrayList<>();
          for (EsFinding document : findingHandler.fetch(arrangeStart, 10_000)) {
            if (!document.getBase_id().equals(credentialsFindingId)) {
              continue;
            }
            document.setFinding_value(CLEARTEXT_CREDENTIALS);
            document.setBase_representative(CLEARTEXT_CREDENTIALS);
            document.setFinding_masking_version(null);
            EsFinding ghost =
                engineService.getObjectMapper().convertValue(document, EsFinding.class);
            ghost.setBase_id(ghostId);
            documents.add(document);
            documents.add(ghost);
          }
          return documents;
        };
    // The cursor must not be past the findings, or the delegated read would not see them
    moveFindingCursorTo(arrangeStart);
    tenantTx.execute(
        TxCtx.allTenants(),
        () ->
            engineService.bulkProcessing(Stream.of(new EsModel<>(EsFinding.class, olderInstance))));
    return ghostId;
  }

  // -- Act helpers --

  private void runMigration() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      migration.migrate(
          new Context() {
            @Override
            public Configuration getConfiguration() {
              return null;
            }

            @Override
            public Connection getConnection() {
              return connection;
            }
          });
    }
  }

  /** One round of the real engine sync of the finding model, as its Quartz trigger runs it. */
  private void runSyncRound() throws Exception {
    JobDataMap jobData = new JobDataMap();
    jobData.put(MODEL_NAME_KEY, FINDING_MODEL);
    jobData.put(ENGINE_CONTEXT_INSTANCE_KEY, engineContext);
    jobData.put(ENGINE_SERVICE_INSTANCE_KEY, engineService);
    jobData.put(TENANT_TRANSACTION_INSTANCE_KEY, tenantTx);
    JobExecutionContext jobContext = mock(JobExecutionContext.class);
    when(jobContext.getMergedJobDataMap()).thenReturn(jobData);
    new EngineSyncExecutionJob.Job().execute(jobContext);
  }

  // -- Assert helpers --

  private Map<String, EsFinding> indexedDocuments() {
    return engineService
        .searchCursorPaged(
            null,
            EsFinding.class,
            new CursorPageQuery(
                Tenant.DEFAULT_TENANT_UUID,
                arrangeStart,
                null,
                Instant.now().plus(1, ChronoUnit.HOURS),
                EngineService.CURSOR_PAGE_MAX_SIZE))
        .stream()
        .collect(Collectors.toMap(EsFinding::getBase_id, Function.identity()));
  }

  private Optional<EsFinding> indexedDocument(String id) {
    return Optional.ofNullable(indexedDocuments().get(id));
  }

  private boolean isMasked(String id) {
    return indexedDocument(id)
        .map(
            document ->
                MASKED_CREDENTIALS.equals(document.getFinding_value())
                    && Long.valueOf(MASKING_VERSION).equals(document.getFinding_masking_version()))
        .orElse(false);
  }

  // -- Tests --

  @Nested
  @DisplayName("When the index holds documents written by an instance that did not mask")
  class WhenTheIndexHoldsCleartext {

    @Test
    @DisplayName("The re-feed masks them in place, without ever wiping the index")
    void given_cleartextDocuments_should_maskThemInPlaceWithoutWipe() throws Exception {
      // Arrange
      String credentialsFinding =
          persistFinding(FindingFixture.createFindingCredentialsWithDistinctPassword());
      String ghost = indexAsOlderInstance(credentialsFinding);
      Awaitility.await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () ->
                  indexedDocument(credentialsFinding)
                      .map(document -> CLEARTEXT_CREDENTIALS.equals(document.getFinding_value()))
                      .orElse(false));
      assertThat(indexedDocument(ghost)).as("the ghost is indexed before the re-feed").isPresent();
      runSyncRound();
      assertThat(indexedDocument(credentialsFinding).orElseThrow().getFinding_value())
          .as("without the re-feed, the regular sync never revisits an unchanged finding")
          .isEqualTo(CLEARTEXT_CREDENTIALS);

      // Act
      runMigration();
      Awaitility.await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofMillis(500))
          .until(
              () -> {
                runSyncRound();
                return isMasked(credentialsFinding);
              });

      // Assert
      EsFinding credentials = indexedDocument(credentialsFinding).orElseThrow();
      assertThat(credentials.getFinding_value()).isEqualTo(MASKED_CREDENTIALS);
      assertThat(credentials.getBase_representative()).isEqualTo(MASKED_CREDENTIALS);
      assertThat(credentials.getFinding_masking_version()).isEqualTo(MASKING_VERSION);
      assertThat(indexedDocument(ghost))
          .as("a document the sync never writes again survives: the index was not wiped")
          .isPresent();
    }
  }
}
