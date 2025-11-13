package io.openaev.executors.sentinelone.service;

import static io.openaev.executors.sentinelone.service.SentinelOneExecutorService.SENTINELONE_EXECUTOR_NAME;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.executors.ExecutorContextService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service(SentinelOneExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class SentinelOneExecutorContextService extends ExecutorContextService {
  public static final String SERVICE_NAME = SENTINELONE_EXECUTOR_NAME;

  @Override
  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent) {}

  @Override
  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus) throws InterruptedException {
    return List.of();
  }
}
