package io.openaev.executors;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.FileService.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.model.Executor;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
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
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ExecutorService extends AbstractConnectorService<Executor, ExecutorOutput>
    implements DependenciesManager {

  public static final String EXT_PNG = ".png";
  @Resource protected ObjectMapper mapper;

  private final ExecutorRepository executorRepository;
  private final ExecutionTraceRepository executionTraceRepository;

  private final FileService fileService;

  private final ExecutorMapper executorMapper;

  private final TenantRepository tenantRepository;

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
      TenantRepository tenantRepository) {
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
    this.tenantRepository = tenantRepository;
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
    return this.executorRepository.findByTypeAndTenantId(type, TenantContext.getCurrentTenant());
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
      executor = new Executor();
      executor.setId(id);
    }

    executor.setName(name);
    executor.setType(type);
    executor.setDoc(documentationUrl);
    executor.setBackgroundColor(backgroundColor);
    executor.setPlatforms(platforms);

    return executorRepository.save(executor);
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

  // -- TENANT DEPENDENCIES --

  /**
   * Copies all built-in executors from the current tenant to the newly created tenant. Since all
   * tenant are supposed to have them and we know for sure that the current tenant exists, we use
   * it. Each executor gets a new UUID so it can coexist alongside the original. Also copies
   * executor images (icons/banners) and catalog connector logos from MinIO so the new tenant can
   * serve them.
   */
  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    try {
      Optional<Tenant> referenceTenant =
          tenantRepository.findFirstByDeletedAtIsNullAndIdNot(tenant.getId());
      if (referenceTenant.isEmpty()) {
        log.info(
            "No reference tenant found — skipping executor copy for tenant {}."
                + " Executors will be created when integrations start.",
            tenant.getId());
        return;
      }
      String referenceTenantId = referenceTenant.get().getId();

      // Copy executor images and catalog connector logos for the new tenant
      fileService.copyExecutorImagesForTenant(referenceTenantId, tenant.getId());
      fileService.copyCatalogConnectorLogosForTenant(referenceTenantId, tenant.getId());

      // Filter by reference tenant to avoid L1 cache pollution from prior creates
      List<Executor> referenceTenantExecutors =
          fromIterable(executorRepository.findAll()).stream()
              .filter(e -> referenceTenantId.equals(e.getTenant().getId()))
              .toList();
      for (Executor source : referenceTenantExecutors) {
        Executor copy = new Executor();
        copy.setId(UUID.randomUUID().toString());
        copy.setName(source.getName());
        copy.setType(source.getType());
        copy.setPlatforms(
            source.getPlatforms() != null ? source.getPlatforms().clone() : new String[0]);
        copy.setDoc(source.getDoc());
        copy.setBackgroundColor(source.getBackgroundColor());
        copy.setTenant(tenant);
        executorRepository.save(copy);
      }
    } catch (Exception e) {
      throw new DependenciesManagerException(
          "Failed to create executors for tenant " + tenant.getName(), e);
    }
  }

  /** Deletes all executors associated with the given tenant during purge. */
  @Override
  public void deleteDependencyForTenant(String tenantId) throws DependenciesManagerException {
    try {
      executorRepository.deleteAllByTenantIdNative(tenantId);
    } catch (Exception e) {
      throw new DependenciesManagerException(
          "Failed to delete executors for tenant " + tenantId, e);
    }
  }
}
