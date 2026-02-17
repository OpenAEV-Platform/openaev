package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.FileService.INJECTORS_IMAGES_BASE_PATH;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.openaev.asset.QueueService;
import io.openaev.config.RabbitmqConfig;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.Contractor;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.injector.form.InjectorCreateInput;
import io.openaev.rest.injector.form.InjectorOutput;
import io.openaev.rest.injector.response.InjectorConnection;
import io.openaev.rest.injector.response.InjectorRegistration;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.form.InjectorContractInput;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.AbstractConnectorService;
import io.openaev.service.exception.InjectorRegistrationException;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.InjectorMapper;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service("coreInjectorService")
// TODO needs to be merged with integrations/InjectorService
public class InjectorService extends AbstractConnectorService<Injector, InjectorOutput> {
  private static final String DUMMY_SUFFIX = "_dummy";

  @Resource private RabbitmqConfig rabbitmqConfig;
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final AttackPatternRepository attackPatternRepository;

  private final FileService fileService;
  private final InjectorContractService injectorContractService;
  private final DomainService domainService;

  private final InjectorMapper injectorMapper;

  private final QueueService queueService;

  @Autowired
  public InjectorService(
      InjectorRepository injectorRepository,
      InjectorContractRepository injectorContractRepository,
      AttackPatternRepository attackPatternRepository,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      FileService fileService,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      InjectorContractService injectorContractService,
      DomainService domainService,
      InjectorMapper injectorMapper,
      CatalogConnectorMapper catalogConnectorMapper,
      QueueService queueService) {
    super(
        ConnectorType.INJECTOR,
        connectorInstanceConfigurationRepository,
        catalogConnectorService,
        connectorInstanceService,
        catalogConnectorMapper);
    this.injectorRepository = injectorRepository;
    this.injectorContractRepository = injectorContractRepository;
    this.attackPatternRepository = attackPatternRepository;
    this.fileService = fileService;
    this.injectorContractService = injectorContractService;
    this.domainService = domainService;
    this.injectorMapper = injectorMapper;
    this.queueService = queueService;
  }

  @Override
  public List<Injector> getAllConnectors() {
    return fromIterable(injectorRepository.findAll());
  }

  @Override
  protected Injector getConnectorById(String injectorId) {
    return injectorRepository.findById(injectorId).orElse(null);
  }

  @Override
  protected InjectorOutput mapToOutput(
      Injector injector,
      CatalogConnector catalogConnector,
      ConnectorInstance instance,
      boolean existingInjector) {
    return injectorMapper.toInjectorOutput(injector, catalogConnector, instance, existingInjector);
  }

  @Override
  protected Injector createNewConnector() {
    return new Injector();
  }

  /**
   * Create a dummmy injector, that is used when importing the starter pack before the real
   * injectors are registered
   *
   * @param injectorType
   * @param injectorName
   * @return
   */
  public Injector createDummyInjector(
      @NotBlank final String injectorType, @NotBlank final String injectorName) {
    Injector injector = new Injector();
    injector.setName("Dummy " + injectorName);
    injector.setType(injectorType + DUMMY_SUFFIX);
    injector.setId(injectorType + DUMMY_SUFFIX);
    injector.setDependencies(ExternalServiceDependency.fromInjectorType(injectorType));
    return injectorRepository.save(injector);
  }

  /**
   * Check if a dummy injector exist for an injector type and delete it
   *
   * @param injectorType
   */
  public void deleteDummyInjectorIfItExists(@NotBlank final String injectorType) {
    injectorRepository.findById(injectorType + DUMMY_SUFFIX).ifPresent(injectorRepository::delete);
  }

  /**
   * This method will check if the injector type is a dummy if yes it will remove the dummy suffix
   * if no it will return the parameter It is used to send the execution to the correct injector
   * even if the current one is just a dummy injector
   *
   * @param injectorType
   * @return
   */
  public String getOriginInjectorType(@NotBlank final String injectorType) {
    if (injectorType.endsWith(DUMMY_SUFFIX)) {
      return injectorType.substring(0, injectorType.length() - DUMMY_SUFFIX.length());
    }
    return injectorType;
  }

  public List<Injector> findAll() {
    return StreamSupport.stream(injectorRepository.findAll().spliterator(), false)
        .collect(Collectors.toList());
  }

  /**
   * Retrieve all injectors.
   *
   * @param isIncludeNext Include pending injectors.
   * @return List of injector output
   */
  public Iterable<InjectorOutput> injectorsOutput(boolean isIncludeNext) {
    return getConnectorsOutput(isIncludeNext);
  }

  /**
   * Find injector by its type
   *
   * @param injectorType injector type to search for
   * @return an Optional containing the injector if found, empty otherwise
   */
  public Optional<Injector> injectorByType(@NotBlank final String injectorType) {
    return injectorRepository.findByType(injectorType);
  }

  /**
   * Retrieves IDs of resources associated with an injector.
   *
   * @param injectorId injector identifier.
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getInjectorRelationsId(String injectorId) {
    return getConnectorRelationsId(injectorId);
  }

  public InjectorRegistration registerExternalInjector(
      InjectorCreateInput input, Optional<MultipartFile> file) {
    ConnectionFactory factory = this.queueService.createConnectionFactory();
    // Declare queueing
    Connection connection = null;
    try {
      // Upload icon
      if (file.isPresent() && "image/png".equals(file.get().getContentType())) {
        fileService.uploadFile(
            FileService.INJECTORS_IMAGES_BASE_PATH + input.getType() + ".png", file.get());
      }
      connection = factory.newConnection();
      this.queueService.createChannel(connection, "_injector_" + input.getType(), input.getType());
      String queueName = rabbitmqConfig.getPrefix() + "_injector_" + input.getType();
      // We need to support upsert for registration
      Injector injector = injectorRepository.findById(input.getId()).orElse(null);
      if (injector == null) {
        Injector injectorChecking = injectorRepository.findByType(input.getType()).orElse(null);
      }
      if (injector != null) {
        updateExistingExternalInjector(
            injector,
            input.getType(),
            input.getName(),
            input.getContracts(),
            input.getCustomContracts(),
            input.getCategory(),
            input.getExecutorCommands(),
            input.getExecutorClearCommands(),
            input.getPayloads());
      } else {
        // save the injector
        Injector newInjector = new Injector();
        newInjector.setId(input.getId());
        newInjector.setExternal(true);
        newInjector.setName(input.getName());
        newInjector.setType(input.getType());
        newInjector.setCategory(input.getCategory());
        newInjector.setCustomContracts(input.getCustomContracts());
        newInjector.setExecutorCommands(input.getExecutorCommands());
        newInjector.setExecutorClearCommands(input.getExecutorClearCommands());
        newInjector.setPayloads(input.getPayloads());
        Injector savedInjector = injectorRepository.save(newInjector);
        // Save the contracts
        List<InjectorContract> injectorContracts =
            input.getContracts().stream()
                .map(in -> injectorContractService.convertInjectorFromInput(in, savedInjector))
                .toList();
        injectorContractRepository.saveAll(injectorContracts);

        // delete the dummy injector if it was created when importing the starter pack
        deleteDummyInjectorIfItExists(input.getType());
      }
      InjectorConnection conn =
          new InjectorConnection(
              rabbitmqConfig.getHostname(),
              rabbitmqConfig.getVhost(),
              rabbitmqConfig.isSsl(),
              rabbitmqConfig.getPort(),
              rabbitmqConfig.getUser(),
              rabbitmqConfig.getPass());
      return new InjectorRegistration(conn, queueName);
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (connection != null) {
        try {
          connection.close();
        } catch (IOException e) {
          log.error(
              "Unable to close RabbitMQ connection. You should worry as this could impact performance",
              e);
        }
      }
    }
  }

  public Injector updateExistingExternalInjector(
      Injector injector,
      String type,
      String name,
      List<InjectorContractInput> contracts,
      Boolean customContracts,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean payloads) {
    injector.setUpdatedAt(Instant.now());
    injector.setType(type);
    injector.setName(name);
    injector.setExternal(true);
    injector.setCustomContracts(customContracts);
    injector.setCategory(category);
    injector.setExecutorCommands(executorCommands);
    injector.setExecutorClearCommands(executorClearCommands);
    injector.setPayloads(payloads);
    List<String> existing = new ArrayList<>();
    List<String> toDeletes = new ArrayList<>();
    injector
        .getContracts()
        .forEach(
            contract -> {
              Optional<InjectorContractInput> current =
                  contracts.stream().filter(c -> c.getId().equals(contract.getId())).findFirst();
              if (current.isPresent()) {
                existing.add(contract.getId());
                contract.setManual(current.get().isManual());
                contract.setLabels(current.get().getLabels());
                contract.setContent(current.get().getContent());
                contract.setAtomicTesting(current.get().isAtomicTesting());
                contract.setPlatforms(current.get().getPlatforms());
                if (!current.get().getAttackPatternsExternalIds().isEmpty()) {
                  List<AttackPattern> attackPatterns =
                      fromIterable(
                          attackPatternRepository.findAllByExternalIdInIgnoreCase(
                              current.get().getAttackPatternsExternalIds()));
                  contract.setAttackPatterns(attackPatterns);
                } else {
                  contract.setAttackPatterns(new ArrayList<>());
                }

                if (!payloads) {
                  Set<Domain> currentDomains =
                      this.domainService.upsertDomainEntities(contract.getDomains());
                  Set<Domain> domainsToAdd = this.domainService.upserts(current.get().getDomains());
                  contract.setDomains(
                      this.domainService.mergeDomains(currentDomains, domainsToAdd));
                }
              } else if (!contract.getCustom()) {
                toDeletes.add(contract.getId());
              }
            });
    List<InjectorContract> toCreates =
        contracts.stream()
            .filter(c -> !existing.contains(c.getId()))
            .map(in -> injectorContractService.convertInjectorFromInput(in, injector))
            .toList();
    injectorContractRepository.deleteAllById(toDeletes);
    injectorContractRepository.saveAll(toCreates);
    return injectorRepository.save(injector);
  }

  // -- BUILT - IN --

  /**
   * Registers or updates an injector and its contracts.
   *
   * <p>This method handles the complete lifecycle of injector registration:
   *
   * <ul>
   *   <li>Uploads injector icons
   *   <li>Creates new injectors or updates existing ones
   *   <li>Synchronizes contracts (create/update/delete)
   * </ul>
   *
   * @param id unique identifier for the injector
   * @param name display name for the injector
   * @param contractor the contractor providing the injector definition
   * @param isCustomizable whether custom contracts can be created
   * @param category the category this injector belongs to
   * @param executorCommands commands for execution
   * @param executorClearCommands commands for cleanup
   * @param isPayloads whether this injector uses payloads
   * @param dependencies external service dependencies
   * @throws InjectorRegistrationException if registration fails due to conflicts or errors
   */
  @Transactional
  public void registerBuiltinInjector(
      String id,
      String name,
      Contractor contractor,
      Boolean isCustomizable,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean isPayloads,
      List<ExternalServiceDependency> dependencies)
      throws InjectorRegistrationException {

    // Upload icon if available
    uploadInjectorIcon(contractor);

    // Validate no ID conflicts exist
    validateNoIdConflict(id, contractor);

    // Get contracts from contractor
    List<Contract> staticContracts;
    try {
      staticContracts = contractor.contracts();
    } catch (Exception e) {
      throw new InjectorRegistrationException(
          "Failed to retrieve contracts from contractor: " + contractor.getType(), e);
    }

    // Find existing injector or create new
    Injector existingInjector = injectorRepository.findById(id).orElse(null);

    if (existingInjector != null) {
      updateExistingBuiltinInjector(
          existingInjector,
          name,
          contractor,
          isCustomizable,
          category,
          executorCommands,
          executorClearCommands,
          isPayloads,
          dependencies,
          staticContracts);
    } else {
      createNewBuiltinInjector(
          id,
          name,
          contractor,
          isCustomizable,
          category,
          executorCommands,
          executorClearCommands,
          isPayloads,
          dependencies,
          staticContracts);

      // delete the dummy injector if it was created when importing the starter pack
      deleteDummyInjectorIfItExists(contractor.getType());
    }

    log.info("Successfully registered injector '{}' (type: {})", name, contractor.getType());
  }

  private void uploadInjectorIcon(Contractor contractor) {
    if (contractor.getIcon() != null) {
      try {
        InputStream iconData = contractor.getIcon().getData();
        fileService.uploadStream(
            INJECTORS_IMAGES_BASE_PATH, contractor.getType() + ".png", iconData);
      } catch (Exception e) {
        log.warn(
            "Failed to upload icon for injector '{}': {}", contractor.getType(), e.getMessage());
      }
    }
  }

  private void validateNoIdConflict(String id, Contractor contractor)
      throws InjectorRegistrationException {
    Injector existingInjector = injectorRepository.findById(id).orElse(null);
    if (existingInjector == null) {
      Optional<Injector> conflictingInjector = injectorRepository.findByType(contractor.getType());
      if (conflictingInjector.isPresent()) {
        throw new InjectorRegistrationException(
            String.format(
                "Injector '%s' already exists with a different ID (%s). "
                    + "Please delete it or contact your administrator.",
                contractor.getType(), conflictingInjector.get().getId()));
      }
    }
  }

  private void updateExistingBuiltinInjector(
      Injector injector,
      String name,
      Contractor contractor,
      Boolean isCustomizable,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean isPayloads,
      List<ExternalServiceDependency> dependencies,
      List<Contract> staticContracts) {

    // Update injector properties
    injector.setExternal(false);
    applyBuiltinInjectorProperties(
        injector,
        name,
        isCustomizable,
        contractor,
        category,
        executorCommands,
        executorClearCommands,
        isPayloads,
        dependencies);

    // Synchronize contracts
    List<String> existingIds = new ArrayList<>();
    List<InjectorContract> toUpdate = new ArrayList<>();
    List<String> toDelete = new ArrayList<>();

    for (InjectorContract contractDB : injector.getContracts()) {
      Optional<Contract> matchingContract =
          staticContracts.stream()
              .filter(contract -> contract.getId().equals(contractDB.getId()))
              .findFirst();

      if (matchingContract.isPresent()) {
        this.injectorContractService.updateBuiltInInjectorContract(
            contractDB, matchingContract.get(), isPayloads);
        existingIds.add(contractDB.getId());
        toUpdate.add(contractDB);
      } else if (shouldDeleteContract(contractDB, injector)) {
        toDelete.add(contractDB.getId());
      }
    }

    // Create new contracts
    List<InjectorContract> toCreate =
        staticContracts.stream()
            .filter(c -> !existingIds.contains(c.getId()))
            .map(
                contract ->
                    this.injectorContractService.createBuiltinInjectorContract(
                        contract, injector, isPayloads))
            .toList();

    // Persist changes
    injectorContractRepository.deleteAllById(toDelete);
    injectorContractRepository.saveAll(toCreate);
    injectorContractRepository.saveAll(toUpdate);
    injectorRepository.save(injector);
  }

  private boolean shouldDeleteContract(InjectorContract contractDB, Injector injector) {
    return !contractDB.getCustom() && (!injector.isPayloads() || contractDB.getPayload() == null);
  }

  private void createNewBuiltinInjector(
      String id,
      String name,
      Contractor contractor,
      Boolean isCustomizable,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean isPayloads,
      List<ExternalServiceDependency> dependencies,
      List<Contract> staticContracts) {

    Injector newInjector = new Injector();
    newInjector.setId(id);
    applyBuiltinInjectorProperties(
        newInjector,
        name,
        isCustomizable,
        contractor,
        category,
        executorCommands,
        executorClearCommands,
        isPayloads,
        dependencies);

    Injector savedInjector = injectorRepository.save(newInjector);

    List<InjectorContract> injectorContracts =
        staticContracts.stream()
            .map(
                contract ->
                    this.injectorContractService.createBuiltinInjectorContract(
                        contract, savedInjector, isPayloads))
            .toList();
    injectorContractRepository.saveAll(injectorContracts);
  }

  private void applyBuiltinInjectorProperties(
      Injector injector,
      String name,
      Boolean isCustomizable,
      Contractor contractor,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean isPayloads,
      List<ExternalServiceDependency> dependencies) {
    injector.setExternal(false);
    injector.setName(name);
    injector.setCustomContracts(isCustomizable);
    injector.setType(contractor.getType());
    injector.setCategory(category);
    injector.setExecutorCommands(executorCommands);
    injector.setExecutorClearCommands(executorClearCommands);
    injector.setPayloads(isPayloads);
    injector.setUpdatedAt(Instant.now());
    injector.setDependencies(dependencies.toArray(new ExternalServiceDependency[0]));
  }
}
