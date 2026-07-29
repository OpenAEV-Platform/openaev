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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;

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
