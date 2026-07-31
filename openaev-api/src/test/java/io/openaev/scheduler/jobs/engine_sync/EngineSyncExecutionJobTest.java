package io.openaev.scheduler.jobs.engine_sync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.config.EngineConfig;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EngineService;
import io.openaev.engine.EsModel;
import io.openaev.engine.model.EsBase;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.Trigger;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngineSyncExecutionJob Unit Tests")
class EngineSyncExecutionJobTest {

  private static final String MODEL_NAME = "test-model";

  @Mock private Scheduler scheduler;
  @Mock private EngineService engineService;
  @Mock private EngineContext engineContext;
  @Mock private EsModel<EsBase> esModel;
  @Mock private JobExecutionContext jobExecutionContext;

  private EngineConfig engineConfig;

  @BeforeEach
  void setUp() {
    engineConfig = new EngineConfig();
    lenient().when(esModel.getName()).thenReturn(MODEL_NAME);
    lenient().when(engineContext.getModels()).thenReturn(List.of(esModel));
    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put("modelName", MODEL_NAME);
    lenient().when(jobExecutionContext.getMergedJobDataMap()).thenReturn(jobDataMap);
  }

  private EngineSyncExecutionJob buildJob() throws Exception {
    return new EngineSyncExecutionJob(scheduler, engineService, engineContext, engineConfig);
  }

  @Test
  @DisplayName("Skips the sync pass when the concurrency cap is reached, without losing the work")
  void given_capReached_should_skipPassAndRetryLater() throws Exception {
    engineConfig.setIndexingMaxConcurrentModels(1);
    EngineSyncExecutionJob job = buildJob();
    EngineSyncExecutionJob.Job pass = job.new Job();
    EngineSyncExecutionJob.Job concurrentPass = job.new Job();

    // While the only permit is held by the running pass, a concurrent pass must skip silently.
    doAnswer(
            invocation -> {
              concurrentPass.execute(jobExecutionContext);
              return null;
            })
        .when(engineService)
        .bulkProcessing(any());

    pass.execute(jobExecutionContext);
    verify(engineService, times(1)).bulkProcessing(any());

    // The permit was released after the pass: the next trigger processes the model again.
    doNothing().when(engineService).bulkProcessing(any());
    concurrentPass.execute(jobExecutionContext);
    verify(engineService, times(2)).bulkProcessing(any());
  }

  @Test
  @DisplayName("Releases the permit when the sync pass fails")
  void given_failingSync_should_releasePermit() throws Exception {
    engineConfig.setIndexingMaxConcurrentModels(1);
    EngineSyncExecutionJob job = buildJob();
    EngineSyncExecutionJob.Job pass = job.new Job();

    doThrow(new RuntimeException("boom")).when(engineService).bulkProcessing(any());
    assertThrows(RuntimeException.class, () -> pass.execute(jobExecutionContext));

    // The failed pass must not leak its permit: the next trigger still gets one.
    doNothing().when(engineService).bulkProcessing(any());
    pass.execute(jobExecutionContext);
    verify(engineService, times(2)).bulkProcessing(any());
  }

  @Test
  @DisplayName("Clamps a non-positive concurrency cap to 1 instead of disabling sync")
  void given_nonPositiveCap_should_clampToOne() throws Exception {
    engineConfig.setIndexingMaxConcurrentModels(0);
    EngineSyncExecutionJob job = buildJob();
    EngineSyncExecutionJob.Job pass = job.new Job();

    pass.execute(jobExecutionContext);
    verify(engineService, times(1)).bulkProcessing(any());
  }

  @Test
  @DisplayName("Registers one job per model once all singletons are instantiated")
  void given_models_should_registerOneJobPerModelAfterContextStartup() throws Exception {
    List<EsModel<EsBase>> models =
        IntStream.range(0, 13)
            .mapToObj(
                i -> {
                  @SuppressWarnings("unchecked")
                  EsModel<EsBase> mdl = mock(EsModel.class);
                  when(mdl.getName()).thenReturn("model-" + i);
                  return mdl;
                })
            .toList();
    when(engineContext.getModels()).thenReturn(models);
    EngineSyncExecutionJob job = buildJob();

    // Registration must not happen at construction time: the model list is a live bean lookup
    // that is only guaranteed complete once the whole context is up.
    verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));

    job.afterSingletonsInstantiated();

    ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);
    verify(scheduler, times(13)).scheduleJob(jobCaptor.capture(), any(Trigger.class));
    // Compare sorted names: the test only guarantees one job per model, not a discovery order.
    assertEquals(
        models.stream()
            .map(m -> "EngineSyncExecutionJob_forModel_" + m.getName())
            .sorted()
            .toList(),
        jobCaptor.getAllValues().stream().map(jd -> jd.getKey().getName()).sorted().toList());
  }

  @Test
  @DisplayName("Staggers trigger phases so the concurrency cap cannot starve the same models")
  void given_models_should_staggerTriggerStartTimes() throws Exception {
    List<EsModel<EsBase>> models =
        IntStream.range(0, 5)
            .mapToObj(
                i -> {
                  @SuppressWarnings("unchecked")
                  EsModel<EsBase> mdl = mock(EsModel.class);
                  when(mdl.getName()).thenReturn("model-" + i);
                  return mdl;
                })
            .toList();
    when(engineContext.getModels()).thenReturn(models);
    EngineSyncExecutionJob job = buildJob();

    job.afterSingletonsInstantiated();

    ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
    verify(scheduler, times(5)).scheduleJob(any(JobDetail.class), triggerCaptor.capture());
    List<Trigger> triggers = triggerCaptor.getAllValues();
    long baseStart = triggers.get(0).getStartTime().getTime();
    for (int i = 1; i < triggers.size(); i++) {
      long offsetMs = triggers.get(i).getStartTime().getTime() - baseStart;
      // Each trigger is shifted by (index % interval) seconds from its own build instant, and
      // trigger i is built after trigger 0, so the offset can never be below i seconds. The upper
      // bound only guards against a wrong phase and is generous to absorb CI noise/GC pauses.
      assertTrue(
          offsetMs >= i * 1000L,
          "Trigger %d should start at least %ds after the first but was offset by %dms"
              .formatted(i, i, offsetMs));
      assertTrue(
          offsetMs < i * 1000L + 5000L,
          "Trigger %d should start ~%ds after the first but was offset by %dms"
              .formatted(i, i, offsetMs));
    }
  }

  @Test
  @DisplayName("Fails the pass when the requested model is unknown")
  void given_unknownModel_should_throwJobExecutionException() throws Exception {
    engineConfig.setIndexingMaxConcurrentModels(1);
    EngineSyncExecutionJob job = buildJob();
    EngineSyncExecutionJob.Job pass = job.new Job();

    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put("modelName", "unknown-model");
    when(jobExecutionContext.getMergedJobDataMap()).thenReturn(jobDataMap);

    assertThrows(JobExecutionException.class, () -> pass.execute(jobExecutionContext));
    verify(engineService, never()).bulkProcessing(any());
  }
}
