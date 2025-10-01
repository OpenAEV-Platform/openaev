package io.openaev.utils.fixtures;

import static io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_NAME;
import static io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_TYPE;
import static io.openaev.executors.openaev.OpenAEVExecutor.OPENAEV_EXECUTOR_ID;
import static io.openaev.executors.openaev.OpenAEVExecutor.OPENAEV_EXECUTOR_NAME;
import static io.openaev.executors.openaev.OpenAEVExecutor.OPENAEV_EXECUTOR_TYPE;
import static io.openaev.executors.tanium.service.TaniumExecutorService.TANIUM_EXECUTOR_NAME;
import static io.openaev.executors.tanium.service.TaniumExecutorService.TANIUM_EXECUTOR_TYPE;

import io.openaev.database.model.Executor;
import io.openaev.database.repository.ExecutorRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExecutorFixture {
  @Autowired ExecutorRepository executorRepository;

  private Executor createOAEVExecutor() {
    Executor executor = new Executor();
    executor.setType(OPENAEV_EXECUTOR_TYPE);
    executor.setId(OPENAEV_EXECUTOR_ID);
    executor.setName(OPENAEV_EXECUTOR_NAME);
    return executor;
  }

  public Executor getDefaultExecutor() {
    Optional<Executor> executorOptional = executorRepository.findByType(OPENAEV_EXECUTOR_TYPE);
    return executorOptional.orElseGet(() -> executorRepository.save(createOAEVExecutor()));
  }

  public Executor createCrowdstrikeExecutor() {
    Executor executor = new Executor();
    executor.setType(CROWDSTRIKE_EXECUTOR_TYPE);
    executor.setName(CROWDSTRIKE_EXECUTOR_NAME);
    executor.setId(UUID.randomUUID().toString());
    return executor;
  }

  private Executor createTaniumExecutor() {
    Executor executor = new Executor();
    executor.setType(TANIUM_EXECUTOR_TYPE);
    executor.setName(TANIUM_EXECUTOR_NAME);
    executor.setId(UUID.randomUUID().toString());
    return executor;
  }

  public Executor getCrowdstrikeExecutor() {
    Optional<Executor> executorOptional = executorRepository.findByType(CROWDSTRIKE_EXECUTOR_TYPE);
    return executorOptional.orElseGet(() -> executorRepository.save(createCrowdstrikeExecutor()));
  }

  public Executor getTaniumExecutor() {
    Optional<Executor> executorOptional = executorRepository.findByType(TANIUM_EXECUTOR_TYPE);
    return executorOptional.orElseGet(() -> executorRepository.save(createTaniumExecutor()));
  }
}
