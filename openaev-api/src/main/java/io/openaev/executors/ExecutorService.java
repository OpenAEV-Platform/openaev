package io.openaev.executors;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.FileService.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.model.Executor;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.executor.form.ExecutorOutput;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.AbstractConnectorService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.ExecutorMapper;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExecutorService extends AbstractConnectorService<Executor, ExecutorOutput> {

  public static final String EXT_PNG = ".png";
  @Resource protected ObjectMapper mapper;

  private final ExecutorRepository executorRepository;
  private final ExecutionTraceRepository executionTraceRepository;

  private final FileService fileService;

  private final ExecutorMapper executorMapper;

  @Autowired
  public ExecutorService(
      ExecutorRepository executorRepository,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      ExecutionTraceRepository executionTraceRepository,
      FileService fileService,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      ExecutorMapper executorMapper,
      CatalogConnectorMapper catalogConnectorMapper) {
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
  }

  @Override
  protected List<Executor> getAllConnectors() {
    return fromIterable(this.executors());
  }

  @Override
  protected Executor getConnectorById(String executorId) {
    return executorRepository.findById(executorId).orElse(null);
  }

  @Override
  protected ExecutorOutput mapToOutput(
      Executor executor,
      CatalogConnector catalogConnector,
      ConnectorInstance instance,
      boolean existingExecutor) {
    return executorMapper.toExecutorOutput(executor, catalogConnector, instance, existingExecutor);
  }

  @Override
  protected Executor createNewConnector() {
    return new Executor();
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
        .findById(id)
        .orElseThrow(() -> new ElementNotFoundException("Executor not found with id: " + id));
  }

  /**
   * Retrieves IDs of resources associated with an executor.
   *
   * @param executorId executor identifier.
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getExecutorRelationsId(String executorId) {
    return getConnectorRelationsId(executorId);
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
      String id,
      String type,
      String name,
      String documentationUrl,
      String backgroundColor,
      InputStream iconData,
      InputStream bannerData,
      String[] platforms)
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

    Executor executor = executorRepository.findById(id).orElse(null);
    if (executor == null) {
      Executor executorChecking = executorRepository.findByType(type).orElse(null);
      if (executorChecking != null) {
        throw new Exception(
            "The executor "
                + type
                + " already exists with a different ID, please delete it or contact your administrator.");
      }

      executor = new Executor();
      executor.setId(id);
    }

    executor.setName(name);
    executor.setType(type);
    executor.setDoc(documentationUrl);
    executor.setBackgroundColor(backgroundColor);
    executor.setPlatforms(platforms);

    executorRepository.save(executor);
    return executor;
  }

  @Transactional
  public void remove(String id) {
    executorRepository.findById(id).ifPresent(executor -> executorRepository.deleteById(id));
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
}
