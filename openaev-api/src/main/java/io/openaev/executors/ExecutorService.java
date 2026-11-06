package io.openaev.executors;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.FileService.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.model.Executor;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.executors.model.ExecutorDispatch;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.executor.form.ExecutorOutput;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.service.InjectExecutionCallbackService;
import io.openaev.service.EndpointService;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.AbstractConnectorService;
import io.openaev.service.exception.ConnectorStatusException;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.ExecutorMapper;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ExecutorService extends AbstractConnectorService<Executor, ExecutorOutput> {

  public static final String EXT_PNG = ".png";
  @Resource protected ObjectMapper mapper;

  private final ExecutorRepository executorRepository;
  private final ExecutionTraceRepository executionTraceRepository;

  private final FileService fileService;

  private final ExecutorMapper executorMapper;

  private final EndpointService endpointService;

  // Resolved lazily: the callback service reaches the execution engine, which reaches executors.
  private final ObjectProvider<InjectExecutionCallbackService> injectExecutionCallbackService;
  private final TenantScopedTransaction tenantTx;

  @Autowired
  public ExecutorService(
      ExecutorRepository executorRepository,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      ExecutionTraceRepository executionTraceRepository,
      FileService fileService,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      ExecutorMapper executorMapper,
      CatalogConnectorMapper catalogConnectorMapper,
      EndpointService endpointService,
      ObjectProvider<InjectExecutionCallbackService> injectExecutionCallbackService,
      TenantScopedTransaction tenantTx) {
    super(
        ConnectorType.EXECUTOR,
        connectorInstanceConfigurationRepository,
        catalogConnectorService,
        connectorInstanceService,
        catalogConnectorMapper);
    this.fileService = fileService;
    this.executorRepository = executorRepository;
    this.executionTraceRepository = executionTraceRepository;
    this.executorMapper = executorMapper;
    this.endpointService = endpointService;
    this.injectExecutionCallbackService = injectExecutionCallbackService;
    this.tenantTx = tenantTx;
  }

  @Override
  protected List<Executor> getAllConnectors() {
    return fromIterable(this.executors());
  }

  @Override
  protected Executor getConnectorById(String executorId) {
    return executorRepository.findByExecutorId(executorId).orElse(null);
  }

  @Override
  protected ExecutorOutput mapToOutput(
      Executor executor,
      String displayName,
      CatalogConnector catalogConnector,
      ConnectorInstance instance,
      boolean existingExecutor) {
    return executorMapper.toExecutorOutput(
        executor, displayName, catalogConnector, instance, existingExecutor);
  }

  @Override
  protected Executor createNewConnector() {
    return new Executor();
  }

  public ExecutorOutput executorOutput(String id) {
    return getConnectorOutput(id);
  }

  /**
   * Retrieve all executors.
   *
   * @param isIncludeNext Include pending executors.
   * @return List of executor output
   */
  public Iterable<ExecutorOutput> executorsOutput(boolean isIncludeNext) {
    return getConnectorsOutput(isIncludeNext);
  }

  /**
   * Find an executor by its id
   *
   * @param id the executor id to search for
   * @return the executor matching the given id
   * @throws ElementNotFoundException if no collector is found with the given type
   */
  public Executor executor(String id) throws ElementNotFoundException {
    return executorRepository
        .findByExecutorId(id)
        .orElseThrow(() -> new ElementNotFoundException("Executor not found with id: " + id));
  }

  /**
   * Retrieves IDs of resources associated with an executor.
   *
   * @param executorId executor identifier.
   * @param tenantId the requesting tenant, resolved by the caller from the request's single-tenant
   *     scope
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getExecutorRelationsId(String executorId, String tenantId) {
    return getConnectorRelationsId(executorId, tenantId);
  }

  /**
   * Retrieve all executors
   *
   * @return List of executors
   */
  public Iterable<Executor> executors() {
    return this.executorRepository.findAll();
  }

  /**
   * Finds an executor by its type.
   *
   * @param type the executor type to search for
   * @return an Optional containing the executor if found, empty otherwise
   */
  public Optional<Executor> executorByType(String type) {
    return this.executorRepository.findByType(type);
  }

  @Transactional
  public Executor register(
      String tenantId,
      String id,
      String type,
      String name,
      String documentationUrl,
      String backgroundColor,
      InputStream iconData,
      InputStream bannerData,
      String[] platforms)
      throws Exception {
    return register(
        tenantId,
        id,
        type,
        name,
        documentationUrl,
        backgroundColor,
        iconData,
        bannerData,
        platforms,
        true);
  }

  @Transactional
  public Executor register(
      String tenantId,
      String id,
      String type,
      String name,
      String documentationUrl,
      String backgroundColor,
      InputStream iconData,
      InputStream bannerData,
      String[] platforms,
      boolean external)
      throws Exception {
    // Sanity checks
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException("Executor ID must not be null or empty.");
    }

    // Save imgs
    if (iconData != null) {
      fileService.uploadStream(EXECUTORS_IMAGES_ICONS_BASE_PATH, type + EXT_PNG, iconData);
    }
    if (bannerData != null) {
      fileService.uploadStream(EXECUTORS_IMAGES_BANNERS_BASE_PATH, type + EXT_PNG, bannerData);
    }

    Executor executor = executorRepository.findByExecutorId(id).orElse(null);
    if (executor == null) {
      executor = new Executor();
      executor.setId(id);
      executor.setTenantId(tenantId);
    }

    executor.setName(name);
    executor.setType(type);
    executor.setDoc(documentationUrl);
    executor.setBackgroundColor(backgroundColor);
    executor.setPlatforms(platforms);
    executor.setExternal(external);

    return executorRepository.save(executor);
  }

  /**
   * Deletes an executor and, when it was deployed through the Integration Manager, the connector
   * instance that owns it - otherwise the deployment keeps running against an executor that no
   * longer exists and recreates it on its next registration heartbeat (see {@link
   * io.openaev.service.connectors.AbstractConnectorService#deleteOwningConnectorInstance}). The
   * instance delete performs the same source-tag cleanup.
   */
  @Transactional
  public void remove(String id) throws ConnectorStatusException {
    Optional<Executor> executorOptional = executorRepository.findByExecutorId(id);
    // A started executor can never be deleted (OpenCTI parity): stop it first.
    executorOptional
        .filter(BaseConnectorEntity::isExternal)
        .ifPresent(executor -> throwIfConnectorRunning(executor, executor.getUpdatedAt()));
    if (deleteOwningConnectorInstance(id)) {
      return;
    }
    executorOptional.ifPresent(
        executor -> {
          endpointService.removeSourceTagsForExecutor(executor.getId(), executor.getTenantId());
          executorRepository.deleteByExecutorId(executor.getId());
        });
  }

  /**
   * Manage agents with no platform: set and save execution traces for the given inject and agents
   * without platform
   *
   * @param agents to manage
   * @param injectStatus to manage
   * @return the agents with platform
   */
  public List<Agent> manageWithoutPlatformAgents(List<Agent> agents, InjectStatus injectStatus) {
    List<Agent> withoutPlatformAgents =
        agents.stream()
            .filter(
                agent ->
                    ((Endpoint) agent.getAsset()).getPlatform() == null
                        || ((Endpoint) agent.getAsset()).getPlatform()
                            == Endpoint.PLATFORM_TYPE.Unknown
                        || ((Endpoint) agent.getAsset()).getArch() == null)
            .toList();
    agents.removeAll(withoutPlatformAgents);
    // Agents with no platform or unknown platform, traces to save
    if (!withoutPlatformAgents.isEmpty()) {
      executionTraceRepository.saveAll(
          withoutPlatformAgents.stream()
              .map(
                  agent ->
                      new ExecutionTrace(
                          injectStatus,
                          ExecutionTraceStatus.ERROR,
                          List.of(),
                          "Unsupported platform: "
                              + ((Endpoint) agent.getAsset()).getPlatform()
                              + " (arch:"
                              + ((Endpoint) agent.getAsset()).getArch()
                              + ")",
                          ExecutionTraceAction.COMPLETE,
                          agent,
                          null))
              .toList());
    }
    return agents;
  }

  /**
   * Sends a payload to one agent through its EDR and, when the EDR call fails, ends that agent's
   * execution in error instead of losing the failure in the logs. One failing agent no longer stops
   * the other agents of the same batch.
   */
  public void sendPayload(ExecutorDispatch dispatch, Runnable send) {
    try {
      send.run();
    } catch (Exception e) {
      log.error(
          "Executor could not send the payload of inject {} to agent {}",
          dispatch.injectId(),
          dispatch.agentId(),
          e);
      reportDispatchFailure(dispatch, "The executor could not send the payload: " + e.getMessage());
    }
  }

  /**
   * Tells the user, on the inject, that the executor is still waiting before it can send the
   * payload to an agent (for example while the EDR keeps another session open on the device).
   */
  public void reportDispatchWaiting(ExecutorDispatch dispatch, String message) {
    reportDispatch(dispatch, ExecutionTraceStatus.INFO, InjectExecutionAction.data, message);
  }

  /**
   * Ends an agent's execution in error, with the reason, when its executor could not send the
   * payload. Without it the inject stays pending until the agent timeout, with no explanation.
   */
  public void reportDispatchFailure(ExecutorDispatch dispatch, String message) {
    reportDispatch(dispatch, ExecutionTraceStatus.ERROR, InjectExecutionAction.complete, message);
  }

  private void reportDispatch(
      ExecutorDispatch dispatch,
      ExecutionTraceStatus status,
      InjectExecutionAction action,
      String message) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setStatus(status.name());
    input.setAction(action);
    input.setMessage(message);
    // Executors send payloads from background threads: set both tenant scopes before writing.
    TenantContext.setCurrentTenant(dispatch.tenantId());
    try {
      tenantTx.execute(
          TxCtx.forTenant(dispatch.tenantId()),
          () -> {
            try {
              injectExecutionCallbackService
                  .getObject()
                  .injectExecutionCallback(dispatch.agentId(), dispatch.injectId(), input);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } catch (Exception e) {
      log.error(
          "Could not report executor dispatch '{}' on inject {} for agent {}",
          message,
          dispatch.injectId(),
          dispatch.agentId(),
          e);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
