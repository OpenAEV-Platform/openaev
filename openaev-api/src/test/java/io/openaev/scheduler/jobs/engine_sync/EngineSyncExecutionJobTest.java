package io.openaev.scheduler.jobs.engine_sync;

import static io.openaev.scheduler.jobs.engine_sync.EngineSyncExecutionJob.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.config.EngineConfig;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.model.EsBase;
import io.openaev.scheduler.CustomSchedulerFactoryFactory;
import io.openaev.utils.RandomUtils;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngineSyncExecutionJob Unit Tests")
class EngineSyncExecutionJobTest {

  private static final String MODEL_NAME = "test-model";

  @Mock private CustomSchedulerFactoryFactory customSchedulerFactoryFactory;
  @Mock private SchedulerFactory schedulerFactory;
  @Mock private Scheduler scheduler;
  @Mock private EngineService engineService;
  @Mock private EngineContext engineContext;
  @Mock private EsModel<EsBase> esModel;
  @Mock private JobExecutionContext jobExecutionContext;
  @Mock private TenantScopedTransaction tenantTx;
  private final RandomUtils randomUtils = new RandomUtils();

  private EngineConfig engineConfig;

  EngineSyncExecutionJobTest() throws NoSuchAlgorithmException {}

  @BeforeEach
  void setUp() throws SchedulerException {
    engineConfig = new EngineConfig();
    lenient()
        .when(customSchedulerFactoryFactory.get(anyString(), anyInt(), anyLong()))
        .thenReturn(schedulerFactory);
    lenient().when(schedulerFactory.getScheduler()).thenReturn(scheduler);
    lenient().when(esModel.getName()).thenReturn(MODEL_NAME);
    lenient().when(engineContext.getModels()).thenReturn(List.of(esModel));
    lenient().when(jobExecutionContext.getMergedJobDataMap()).thenReturn(setupDataMap());
    // The tenant-scoped primitive runs the work it is given; the transaction plumbing itself is
    // covered by TenantScopedTransaction's own tests.
    lenient()
        .doAnswer(
            invocation -> {
              invocation.getArgument(1, Runnable.class).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), any(Runnable.class));
  }

  private EngineSyncExecutionJob buildJob() throws Exception {
    return new EngineSyncExecutionJob(
        customSchedulerFactoryFactory,
        engineService,
        engineContext,
        engineConfig,
        randomUtils,
        tenantTx);
  }

  private JobDataMap setupDataMap() {
    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put(MODEL_NAME_KEY, MODEL_NAME);
    jobDataMap.put(ENGINE_CONTEXT_INSTANCE_KEY, engineContext);
    jobDataMap.put(ENGINE_SERVICE_INSTANCE_KEY, engineService);
    jobDataMap.put(TENANT_TRANSACTION_INSTANCE_KEY, tenantTx);
    return jobDataMap;
  }

  private List<EsModel<EsBase>> mockModels(int count) {
    return IntStream.range(0, count)
        .mapToObj(
            i -> {
              @SuppressWarnings("unchecked")
              EsModel<EsBase> mdl = mock(EsModel.class);
              when(mdl.getName()).thenReturn("model-" + i);
              return mdl;
            })
        .toList();
  }

  @Test
  @DisplayName("Propagates a sync failure without preventing the next pass")
  void given_failingSync_should_notPreventNextPass() throws Exception {
    EngineSyncExecutionJob.Job pass = new EngineSyncExecutionJob.Job();

    doThrow(new RuntimeException("boom")).when(engineService).bulkProcessing(any());
    assertThrows(RuntimeException.class, () -> pass.execute(jobExecutionContext));

    // A failed pass must not leave any state behind that would block the next trigger.
    doNothing().when(engineService).bulkProcessing(any());
    pass.execute(jobExecutionContext);
    verify(engineService, times(2)).bulkProcessing(any());
  }

  @Test
  @DisplayName("Clamps a non-positive concurrency cap to 1 instead of disabling sync")
  void given_nonPositiveCap_should_clampToOne() throws Exception {
    engineConfig.setIndexingMaxConcurrentModels(0);

    buildJob();

    // The clamp lives in EngineConfig's getter: the scheduler thread pool must be built with 1
    // thread, never 0 (which would disable engine sync entirely).
    verify(customSchedulerFactoryFactory).get(anyString(), eq(1), anyLong());
  }

  @Test
  @DisplayName("Registers one staggered job per model and starts the scheduler")
  void given_models_should_registerOneStaggeredJobPerModel() throws Exception {
    List<EsModel<EsBase>> models = mockModels(5);
    when(engineContext.getModels()).thenReturn(models);
    when(scheduler.isStarted()).thenReturn(false);
    EngineSyncExecutionJob job = buildJob();

    job.register();

    ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);
    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
    verify(scheduler, times(5)).scheduleJob(jobCaptor.capture(), triggerCaptor.capture());
    // Compare sorted names: the test only guarantees one job per model, not a discovery order.
    assertEquals(
        models.stream()
            .map(m -> "EngineSyncExecutionJob_forModel_" + m.getName())
            .sorted()
            .toList(),
        jobCaptor.getAllValues().stream().map(jd -> jd.getKey().getName()).sorted().toList());

    List<Trigger> triggers = triggerCaptor.getAllValues();
    long baseStart = triggers.get(0).getStartTime().getTime();
    for (int i = 1; i < triggers.size(); i++) {
      long offsetMs = triggers.get(i).getStartTime().getTime() - baseStart;
      // Trigger i starts (i seconds + a sub-second random jitter) after its own build instant,
      // trigger 0 starts (0 seconds + jitter) after its own: the offset between them is at least
      // (i - 1) seconds even in the worst jitter combination. The upper bound only guards against
      // a wrong phase and is generous to absorb CI noise/GC pauses.
      assertTrue(
          offsetMs >= (i - 1) * 1000L,
          "Trigger %d should start at least %ds after the first but was offset by %dms"
              .formatted(i, i - 1, offsetMs));
      assertTrue(
          offsetMs < i * 1000L + 5000L,
          "Trigger %d should start ~%ds after the first but was offset by %dms"
              .formatted(i, i, offsetMs));
      // Each trigger carries a distinct priority so simultaneous fires are broken fairly.
      assertEquals(i, triggers.get(i).getPriority());
    }
    verify(scheduler).start();
  }

  @Test
  @DisplayName("Does not restart an already-started scheduler")
  void given_startedScheduler_should_notStartItAgain() throws Exception {
    when(scheduler.isStarted()).thenReturn(true);
    EngineSyncExecutionJob job = buildJob();

    job.register();

    verify(scheduler, never()).start();
  }

  @Test
  @DisplayName("Fails the pass when the requested model is unknown")
  void given_unknownModel_should_throwJobExecutionException() throws Exception {
    EngineSyncExecutionJob.Job pass = new EngineSyncExecutionJob.Job();

    JobDataMap jobDataMap = setupDataMap();
    jobDataMap.put(MODEL_NAME_KEY, "unknown-model");
    when(jobExecutionContext.getMergedJobDataMap()).thenReturn(jobDataMap);

    assertThrows(JobExecutionException.class, () -> pass.execute(jobExecutionContext));
    verify(engineService, never()).bulkProcessing(any());
  }

  @Test
  @DisplayName("Runs the sync pass inside an all-tenants scoped transaction")
  void given_syncPass_should_runInsideAllTenantsScope() throws Exception {
    // The indexing sweep reads tenant-activated tables (e.g. collectors) to build the search
    // documents, and can_access_tenant is fail-closed: a scope-less sweep silently reads those
    // tables empty and drops the collector-to-security-platform attribution from every indexed
    // expectation. The sweep must therefore always carry the allTenants() intention.
    new EngineSyncExecutionJob.Job().execute(jobExecutionContext);

    ArgumentCaptor<TxCtx> scope = ArgumentCaptor.forClass(TxCtx.class);
    verify(tenantTx).execute(scope.capture(), any(Runnable.class));
    assertEquals(TxCtx.allTenants(), scope.getValue(), "the sweep must carry the allTenants scope");
    verify(engineService).bulkProcessing(any());
  }
}
