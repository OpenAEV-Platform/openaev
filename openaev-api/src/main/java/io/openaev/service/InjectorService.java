package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.FileService.INJECTORS_IMAGES_BASE_PATH;

import io.openaev.context.TenantContext;
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
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectIndexCleanupService;
import io.openaev.rest.injector.form.InjectorCreateInput;
import io.openaev.rest.injector.form.InjectorOutput;
import io.openaev.rest.injector.response.InjectorRegistration;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.form.InjectorContractInput;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.AbstractConnectorService;
import io.openaev.service.connectors.PlatformConnectors;
import io.openaev.service.exception.ConnectorStatusException;
import io.openaev.service.exception.InjectorRegistrationException;
import io.openaev.service.organization.OrganizationService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.InjectorMapper;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotBlank;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service("coreInjectorService")
// TODO needs to be merged with integrations/InjectorService
public class InjectorService extends AbstractConnectorService<Injector, InjectorOutput> {

  // Built-in injectors (Email, Manual, HTTP query, ...) are shipped by the
  // platform, so their contracts are authored by Filigran.
  private static final String BUILTIN_INJECTOR_AUTHOR = "Filigran";

  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final AttackPatternRepository attackPatternRepository;

  private final FileService fileService;
  private final InjectorContractService injectorContractService;
  private final DomainService domainService;
  private final OrganizationService organizationService;

  private final InjectorMapper injectorMapper;

  private final RabbitmqService rabbitmqService;

  private final EntityManager entityManager;

  private final InjectIndexCleanupService injectIndexCleanupService;

  @Autowired
  public InjectorService(
      InjectorRepository injectorRepository,
      InjectorContractRepository injectorContractRepository,
      AttackPatternRepository attackPatternRepository,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      FileService fileService,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      @Lazy InjectorContractService injectorContractService,
      DomainService domainService,
      OrganizationService organizationService,
      InjectorMapper injectorMapper,
      CatalogConnectorMapper catalogConnectorMapper,
      RabbitmqService rabbitmqService,
      EntityManager entityManager,
      InjectIndexCleanupService injectIndexCleanupService) {
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
    this.organizationService = organizationService;
    this.injectorMapper = injectorMapper;
    this.rabbitmqService = rabbitmqService;
    this.entityManager = entityManager;
    this.injectIndexCleanupService = injectIndexCleanupService;
  }

  @Override
  public List<Injector> getAllConnectors() {
    return fromIterable(injectorRepository.findAll());
  }

  @Override
  protected Injector getConnectorById(String injectorId) {
    return injectorRepository
        .findByIdAndTenantId(injectorId, TenantContext.getCurrentTenant())
        .orElse(null);
  }

  @Override
  protected InjectorOutput mapToOutput(
      Injector injector,
      String displayName,
      CatalogConnector catalogConnector,
      ConnectorInstance instance) {
    return injectorMapper.toInjectorOutput(injector, displayName, catalogConnector, instance);
  }

  @Override
  protected Injector createNewConnector() {
    return new Injector();
  }

  /**
   * Deletes an injector together with the contracts it would leave behind.
   *
   * <p>The join rows cascade with the injector at database level, so deleting the row alone strands
   * its contracts with no injector at all: no type in the arsenal, nothing able to run them, and
   * invisible to the maintenance loop of the injector that owned them. Contracts still linked to
   * another injector are kept - built-in contracts are shared across injectors of the same type.
   *
   * <p>Contract deletion cascades to injects at database level with no JPA lifecycle event, so the
   * doomed injects are collected first and de-indexed explicitly afterwards.
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteInjector(@NotBlank final String injectorId) throws ConnectorStatusException {
    String tenantId = TenantContext.getCurrentTenant();
    Injector injector =
        injectorRepository
            .findByIdAndTenantId(injectorId, tenantId)
            .orElseThrow(ElementNotFoundException::new);
    if (PlatformConnectors.isPlatformInjector(injector.getType())) {
      throw new BadRequestException(
          "The implant injector is required by the platform and cannot be deleted");
    }
    // A started injector can never be deleted (OpenCTI parity): stop it first.
    if (injector.isExternal()) {
      throwIfConnectorRunning(injector, injector.getUpdatedAt());
    }
    List<String> orphanedContractIds =
        injectorContractRepository.findByInjectorsContaining(injector).stream()
            .filter(
                contract ->
                    contract.getInjectors().stream()
                        .allMatch(linked -> injectorId.equals(linked.getId())))
            .map(InjectorContract::getId)
            .toList();
    List<String> cascadeDeletedInjectIds =
        injectIndexCleanupService.injectIdsByContractIds(orphanedContractIds, tenantId);
    injectorContractRepository.deleteAllByIdAndTenantId(
        orphanedContractIds.toArray(new String[0]), tenantId);
    // Tear the deployment down with the injector, and only delete the row ourselves when the
    // injector was not deployed through the Integration Manager.
    if (!deleteOwningConnectorInstance(injectorId)) {
      injectorRepository.deleteByIdAndTenantId(injectorId, tenantId);
    }
    injectIndexCleanupService.notifyEngineOfDeletedInjects(cascadeDeletedInjectIds);
  }

  public Injector injector(String id) {
    return injectorRepository
        .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
        .orElseThrow(() -> new ElementNotFoundException("Injector not found with id: " + id));
  }

  public List<Injector> findAll() {
    return StreamSupport.stream(injectorRepository.findAll().spliterator(), false)
        .collect(Collectors.toList());
  }

  public List<Injector> findAllByIds(List<String> ids, String tenantId) {
    List<ConnectorCompositeId> compositeIds =
        ids.stream().map(id -> new ConnectorCompositeId(id, tenantId)).toList();
    return injectorRepository.findAllById(compositeIds);
  }

  public InjectorOutput injectorOutput(String id) {
    return getConnectorOutput(id);
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
    try {
      // Upload icon
      if (file.isPresent() && "image/png".equals(file.get().getContentType())) {
        fileService.uploadFile(
            FileService.INJECTORS_IMAGES_BASE_PATH + input.getType() + ".png", file.get());
      }
      String queueName = this.rabbitmqService.registerQueue(input.getId());
      // Contracts declared by an external injector are attributed to a publisher
      // organization: the source-declared author when provided, otherwise the
      // injector's own name (so a connector's content is never left authorless
      // nor mis-attributed to a generic default).
      Organization authorOrganization = resolveInjectorAuthor(input.getAuthor(), input.getName());
      // We need to support upsert for registration
      Injector injector =
          injectorRepository
              .findByIdAndTenantId(input.getId(), TenantContext.getCurrentTenant())
              .orElse(null);
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
            input.getPayloads(),
            authorOrganization);
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
        newInjector.setTenantId(TenantContext.getCurrentTenant());
        Injector savedInjector = injectorRepository.save(newInjector);
        // Save the contracts
        List<InjectorContract> injectorContracts =
            input.getContracts().stream()
                .map(in -> injectorContractService.convertInjectorFromInput(in, savedInjector))
                .peek(contract -> applyContractAuthor(contract, authorOrganization))
                .toList();
        injectorContracts = fromIterable(injectorContractRepository.saveAll(injectorContracts));
        // Link managed instances returned by saveAll() via the join entity. Contracts imported by
        // the starter pack before this registration are merged by id and adopted here.
        injectorContracts.forEach(savedInjector::linkContract);
        injectorRepository.save(savedInjector);
      }
      return new InjectorRegistration(rabbitmqService.getConnectionInfo(), queueName);
    } catch (Exception e) {
      throw new RuntimeException(e);
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
      Boolean payloads,
      Organization authorOrganization) {
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
                // Re-attribute on every registration so a corrected/late author
                // declaration heals existing rows (and overwrites the historical
                // Filigran mis-attribution).
                applyContractAuthor(contract, authorOrganization);
                contract.setManual(current.get().isManual());
                contract.setLabels(current.get().getLabels());
                contract.setContent(current.get().getContent());
                contract.setAtomicTesting(current.get().isAtomicTesting());
                contract.setPlatforms(current.get().getPlatforms());
                if (!current.get().getAttackPatternsExternalIds().isEmpty()) {
                  List<AttackPattern> attackPatterns =
                      fromIterable(
                          attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(
                              current.get().getAttackPatternsExternalIds(),
                              injector.getTenantId()));
                  contract.setAttackPatterns(attackPatterns);
                } else {
                  contract.setAttackPatterns(new ArrayList<>());
                }

                if (!payloads) {
                  Set<Domain> currentDomains =
                      this.domainService.upsertDomainEntities(
                          contract.getDomains(), injector.getTenantId());
                  Set<Domain> domainsToAdd =
                      this.domainService.upserts(
                          current.get().getDomains(), injector.getTenantId());
                  contract.setDomains(
                      this.domainService.mergeDomains(
                          currentDomains, domainsToAdd, new Tenant(injector.getTenantId())));
                }
              } else if (!contract.getCustom()) {
                toDeletes.add(contract.getId());
              }
            });
    List<InjectorContract> toCreates =
        contracts.stream()
            .filter(c -> !existing.contains(c.getId()))
            .map(in -> injectorContractService.convertInjectorFromInput(in, injector))
            .peek(contract -> applyContractAuthor(contract, authorOrganization))
            .toList();
    // Contract deletion cascades to injects at the DB level (ON DELETE CASCADE): de-index the
    // doomed injects explicitly, no JPA lifecycle event fires for them.
    List<String> cascadeDeletedInjectIds =
        injectIndexCleanupService.injectIdsByContractIds(toDeletes, injector.getTenantId());
    injectorContractRepository.deleteAllByIdAndTenantId(
        toDeletes.toArray(new String[0]), injector.getTenantId());
    injectIndexCleanupService.notifyEngineOfDeletedInjects(cascadeDeletedInjectIds);
    // Unlink deleted contracts via the join entity
    injector.getContracts().stream()
        .filter(c -> toDeletes.contains(c.getId()))
        .toList()
        .forEach(injector::unlinkContract);
    toCreates = fromIterable(injectorContractRepository.saveAll(toCreates));
    // Link managed instances returned by saveAll() via the join entity
    toCreates.forEach(injector::linkContract);
    return injectorRepository.save(injector);
  }

  /**
   * Resolves the publisher organization for an injector's contracts: the source-declared author
   * when present, otherwise the injector name (never a generic default).
   */
  private Organization resolveInjectorAuthor(String declaredAuthor, String injectorName) {
    String author =
        declaredAuthor != null && !declaredAuthor.isBlank() ? declaredAuthor : injectorName;
    return organizationService.findOrCreateByName(author);
  }

  /**
   * Stamps a contract with its publisher organization. Payload-based contracts resolve their author
   * from the payload, so only payload-less contracts (the norm for external injectors) are stamped.
   */
  private void applyContractAuthor(InjectorContract contract, Organization authorOrganization) {
    if (authorOrganization != null && contract.getPayload() == null) {
      contract.setAuthorOrganization(authorOrganization);
    }
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
  @Transactional(rollbackFor = Exception.class)
  public void registerBuiltinInjector(
      String tenantId,
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

    // Get contracts from contractor
    List<Contract> staticContracts;
    try {
      staticContracts = contractor.contracts();
    } catch (Exception e) {
      throw new InjectorRegistrationException(
          "Failed to retrieve contracts from contractor: " + contractor.getType(), e);
    }

    // Find existing injector or create new
    Injector existingInjector = injectorRepository.findByIdAndTenantId(id, tenantId).orElse(null);

    if (existingInjector != null) {
      updateExistingBuiltinInjector(
          existingInjector,
          tenantId,
          name,
          contractor,
          isCustomizable,
          category,
          executorCommands,
          executorClearCommands,
          isPayloads,
          staticContracts);
    } else {
      createNewBuiltinInjector(
          tenantId,
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
    }

    // A payload injector (e.g. the OpenAEV implant) has no static contracts to merge by id: its
    // contracts are payload-driven and created on the fly. Starter-pack imports on a fresh platform
    // run before this injector exists, so their payload contracts are persisted with a payload but
    // no injector link. Adopt those orphans here so they stop showing a question mark / "no payload
    // attached" and become executable.
    if (Boolean.TRUE.equals(isPayloads)) {
      adoptOrphanPayloadContracts(id, tenantId);
    }

    log.info("Successfully registered injector '{}' (type: {})", name, contractor.getType());
  }

  /**
   * Links payload-bearing contracts that are not attached to any injector to this payload injector.
   * Idempotent: contracts already linked to an injector are skipped by the query, and the link
   * insert is {@code ON CONFLICT DO NOTHING}.
   */
  private void adoptOrphanPayloadContracts(String injectorId, String tenantId) {
    List<String> orphanContractIds =
        injectorContractRepository.findContractIdsWithPayloadAndNoInjector(tenantId);
    if (orphanContractIds.isEmpty()) {
      return;
    }
    for (String contractId : orphanContractIds) {
      injectorRepository.linkContract(injectorId, contractId, tenantId);
    }
    log.info(
        "Adopted {} orphan payload contract(s) into injector '{}'",
        orphanContractIds.size(),
        injectorId);
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

  private void updateExistingBuiltinInjector(
      Injector injector,
      String tenantId,
      String name,
      Contractor contractor,
      Boolean isCustomizable,
      String category,
      Map<String, String> executorCommands,
      Map<String, String> executorClearCommands,
      Boolean isPayloads,
      List<Contract> staticContracts) {

    injector.setName(name);
    injector.setType(contractor.getType());
    injector.setCategory(category);
    injector.setExternal(false);
    injector.setCustomContracts(Boolean.TRUE.equals(isCustomizable));
    injector.setPayloads(Boolean.TRUE.equals(isPayloads));
    // Refresh executor commands so existing deployments pick up new per-executor launch strategies
    // (e.g. the MDE detached scheduled task) instead of keeping the first-registration values.
    injector.setExecutorCommands(executorCommands);
    injector.setExecutorClearCommands(executorClearCommands);
    injectorRepository.save(injector);

    // Filter to this tenant's contracts — belt-and-suspenders check since the @JoinTable
    // and tenant filter should already scope the collection correctly.
    List<InjectorContract> tenantContracts =
        injector.getContracts().stream()
            .filter(c -> tenantId.equals(c.getCompositeId().getTenantId()))
            .toList();

    // Synchronize contracts
    List<String> existingIds = new ArrayList<>();
    List<InjectorContract> toUpdate = new ArrayList<>();
    List<String> toDelete = new ArrayList<>();

    for (InjectorContract contractDB : tenantContracts) {
      Optional<Contract> matchingContract =
          staticContracts.stream()
              .filter(contract -> contract.getId().equals(contractDB.getId()))
              .findFirst();

      if (matchingContract.isPresent()) {
        this.injectorContractService.updateBuiltInInjectorContract(
            contractDB, matchingContract.get(), isPayloads, injector);
        existingIds.add(contractDB.getId());
        toUpdate.add(contractDB);
      } else if (shouldDeleteContract(contractDB, injector, staticContracts)) {
        toDelete.add(contractDB.getId());
      }
    }

    // Create new contracts
    Organization builtinAuthor = organizationService.findOrCreateByName(BUILTIN_INJECTOR_AUTHOR);
    List<InjectorContract> toCreate =
        staticContracts.stream()
            .filter(c -> !existingIds.contains(c.getId()))
            .map(
                contract ->
                    this.injectorContractService.createBuiltinInjectorContract(
                        contract, injector, isPayloads))
            .peek(contract -> applyContractAuthor(contract, builtinAuthor))
            .toList();
    // Re-affirm authorship on refreshed built-in contracts too, so any historical
    // gap is healed on the next startup registration.
    toUpdate.forEach(contract -> applyContractAuthor(contract, builtinAuthor));

    // Persist changes. Contract deletion cascades to injects at the DB level (ON DELETE
    // CASCADE): de-index the doomed injects explicitly, no JPA lifecycle event fires for them.
    List<String> cascadeDeletedInjectIds =
        injectIndexCleanupService.injectIdsByContractIds(toDelete, injector.getTenantId());
    injectorContractRepository.deleteAllByIdAndTenantId(
        toDelete.toArray(new String[0]), injector.getTenantId());
    injectIndexCleanupService.notifyEngineOfDeletedInjects(cascadeDeletedInjectIds);
    toCreate = fromIterable(injectorContractRepository.saveAll(toCreate));

    // Link new contracts to the injector via idempotent native INSERT (ON CONFLICT DO NOTHING).
    // Cannot use injectorRepository.save(injector) to sync the join table: it would issue
    // UPDATE injectors SET ... WHERE injector_id = ? (no tenant_id) hitting all tenants.
    for (InjectorContract contract : toCreate) {
      injectorRepository.linkContract(injector.getId(), contract.getId(), tenantId);
    }
  }

  /**
   * Decides whether a DB contract that is missing from the contractor's static catalog should be
   * removed during builtin re-registration.
   *
   * <p>Payload injectors keep contracts still linked to a payload. Injectors that expose no static
   * contracts (dynamic synthesis only, e.g. phishing landing pages) also keep theirs: the owning
   * service creates/deletes those contracts. Without this guard, every restart wiped phishing
   * arsenal actions because {@code PhishingContract.contracts()} is empty.
   */
  private boolean shouldDeleteContract(
      InjectorContract contractDB, Injector injector, List<Contract> staticContracts) {
    if (Boolean.TRUE.equals(contractDB.getCustom())) {
      return false;
    }
    if (injector.isPayloads()) {
      return contractDB.getPayload() == null;
    }
    // Dynamic-only injectors: leave synthesized contracts alone.
    if (staticContracts == null || staticContracts.isEmpty()) {
      return false;
    }
    return true;
  }

  private Injector createNewBuiltinInjector(
      String tenantId,
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

    newInjector.setTenantId(tenantId);
    Injector savedInjector = injectorRepository.save(newInjector);

    Organization builtinAuthor = organizationService.findOrCreateByName(BUILTIN_INJECTOR_AUTHOR);
    List<InjectorContract> injectorContracts =
        staticContracts.stream()
            .map(
                contract ->
                    this.injectorContractService.createBuiltinInjectorContract(
                        contract, savedInjector, isPayloads))
            .peek(contract -> applyContractAuthor(contract, builtinAuthor))
            .toList();
    injectorContracts = fromIterable(injectorContractRepository.saveAll(injectorContracts));
    // Link managed contracts via the join entity so Hibernate populates the join table.
    // We MUST use the instances returned by saveAll() — the originals are detached after merge().
    injectorContracts.forEach(savedInjector::linkContract);
    // Flush now so that all inserts are visible before any subsequent query triggers auto-flush,
    // which would otherwise fail with TransientObjectException.
    entityManager.flush();
    return savedInjector;
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
