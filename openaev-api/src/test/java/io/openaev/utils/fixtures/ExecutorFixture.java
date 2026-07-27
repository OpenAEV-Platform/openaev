package io.openaev.utils.fixtures;

import static io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_TYPE;
import static io.openaev.integration.impl.executors.openaev.OpenAEVExecutorIntegration.*;
import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_TYPE;
import static io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_TYPE;

import io.openaev.database.model.Executor;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExecutorRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExecutorFixture {
  @Autowired ExecutorRepository executorRepository;

  public Executor createOAEVExecutor() {
    return createOAEVExecutor(Tenant.DEFAULT_TENANT_UUID);
  }

  public Executor createOAEVExecutor(String tenantId) {
    Executor executor = new Executor();
    executor.setType(OPENAEV_EXECUTOR_TYPE);
    executor.setId(OPENAEV_EXECUTOR_ID);
    executor.setName(OPENAEV_EXECUTOR_NAME);
    executor.setBackgroundColor(OPENAEV_EXECUTOR_BACKGROUND_COLOR);
    executor.setTenantId(tenantId);
    return executor;
  }

  public Executor createDefaultExecutor(String executorName) {
    Executor executor = new Executor();
    executor.setType(executorName.toLowerCase().replace(" ", "-"));
    executor.setName(executorName);
    executor.setId(UUID.randomUUID().toString());
    executor.setTenantId(Tenant.DEFAULT_TENANT_UUID);
    return executor;
  }

  public Executor getDefaultExecutor() {
    return getDefaultExecutor(Tenant.DEFAULT_TENANT_UUID);
  }

  /**
   * Same as {@link #getDefaultExecutor()} but for a caller operating under a non-default tenant
   * (e.g. a test that created its own tenant): {@code Executor} carries no ambient tenant default
   * (the v1 {@code @Filter}/listener was removed at v2 go-live, see {@link Executor}'s javadoc), so
   * the fallback creation must be told explicitly which tenant to attach to, matching whichever
   * tenant the rest of the fixture data (e.g. an {@code Agent}) is being persisted under.
   */
  public Executor getDefaultExecutor(String tenantId) {
    Optional<Executor> executorOptional = executorRepository.findByType(OPENAEV_EXECUTOR_TYPE);
    return executorOptional.orElseGet(() -> executorRepository.save(createOAEVExecutor(tenantId)));
  }

  public Executor createCrowdstrikeExecutor() {
    Executor executor = new Executor();
    executor.setType(CROWDSTRIKE_EXECUTOR_TYPE);
    executor.setName(CROWDSTRIKE_EXECUTOR_NAME);
    executor.setId(UUID.randomUUID().toString());
    executor.setTenantId(Tenant.DEFAULT_TENANT_UUID);
    return executor;
  }

  private Executor createTaniumExecutor() {
    Executor executor = new Executor();
    executor.setType(TANIUM_EXECUTOR_TYPE);
    executor.setName(TANIUM_EXECUTOR_NAME);
    executor.setId(UUID.randomUUID().toString());
    executor.setTenantId(Tenant.DEFAULT_TENANT_UUID);
    return executor;
  }

  public Executor createSentineloneExecutor() {
    Executor executor = new Executor();
    executor.setType(SENTINELONE_EXECUTOR_TYPE);
    executor.setName(SENTINELONE_EXECUTOR_NAME);
    executor.setId(UUID.randomUUID().toString());
    executor.setTenantId(Tenant.DEFAULT_TENANT_UUID);
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

  public Executor getSentineloneExecutor() {
    Optional<Executor> executorOptional = executorRepository.findByType(SENTINELONE_EXECUTOR_TYPE);
    return executorOptional.orElseGet(() -> executorRepository.save(createSentineloneExecutor()));
  }
}
