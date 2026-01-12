package io.openaev.integrations;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.FileService.INJECTORS_IMAGES_BASE_PATH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.DomainRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.injector_contract.Contract;
import io.openaev.injector_contract.Contractor;
import io.openaev.service.FileService;
import jakarta.transaction.Transactional;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing injector registration and lifecycle.
 *
 * <p>This service handles:
 *
 * <ul>
 *   <li>Registration and update of injectors from contractors
 *   <li>Management of injector contracts (create, update, delete)
 *   <li>Domain upsert and merge operations
 *   <li>Attack pattern associations (MITRE ATT&CK)
 *   <li>Injector icon management
 * </ul>
 *
 * <p>Injector registration is transactional - if any part of the registration fails, all changes
 * are rolled back.
 *
 * @see Contractor for defining injector capabilities
 * @see Contract for individual injection contracts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InjectorService {

  /** Default domain name for uncategorized contracts. */
  private static final String TO_CLASSIFY = "To classify";

  /** Default color for new domains. */
  private static final String DEFAULT_DOMAIN_COLOR = "#FFFFFF";

  private final ObjectMapper mapper;
  private final FileService fileService;
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final DomainRepository domainRepository;

  /**
   * Registers or updates an injector and its contracts.
   *
   * <p>This method handles the complete lifecycle of injector registration:
   *
   * <ul>
   *   <li>Removes hidden injectors (expose=false)
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
  public void register(
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

    // Handle non-exposed injectors
    if (!contractor.isExpose()) {
      handleNonExposedInjector(id);
      return;
    }

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
      updateExistingInjector(
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
      createNewInjector(
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

    log.info("Successfully registered injector '{}' (type: {})", name, contractor.getType());
  }

  private void handleNonExposedInjector(String id) {
    injectorRepository.findById(id).ifPresent(injector -> injectorRepository.deleteById(id));
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

  private void updateExistingInjector(
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
    injector.setName(name);
    injector.setExternal(false);
    injector.setCustomContracts(isCustomizable);
    injector.setType(contractor.getType());
    injector.setCategory(category);
    injector.setExecutorCommands(executorCommands);
    injector.setExecutorClearCommands(executorClearCommands);
    injector.setPayloads(isPayloads);
    injector.setUpdatedAt(Instant.now());
    injector.setDependencies(dependencies.toArray(new ExternalServiceDependency[0]));

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
        updateContract(contractDB, matchingContract.get(), isPayloads);
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
            .map(contract -> createInjectorContract(contract, injector, isPayloads))
            .toList();

    // Persist changes
    injectorContractRepository.deleteAllById(toDelete);
    injectorContractRepository.saveAll(toCreate);
    injectorContractRepository.saveAll(toUpdate);
    injectorRepository.save(injector);
  }

  private void updateContract(
      InjectorContract contractDB, Contract sourceContract, boolean isPayloads) {
    contractDB.setManual(sourceContract.isManual());
    contractDB.setAtomicTesting(sourceContract.isAtomicTesting());
    contractDB.setPlatforms(sourceContract.getPlatforms().toArray(new PLATFORM_TYPE[0]));
    contractDB.setNeedsExecutor(sourceContract.isNeedsExecutor());

    Map<String, String> labels =
        sourceContract.getLabel().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
    contractDB.setLabels(labels);

    // Update attack patterns if not overridden
    if (contractDB.getAttackPatterns().isEmpty()
        && !sourceContract.getAttackPatternsExternalIds().isEmpty()) {
      List<AttackPattern> attackPatterns =
          fromIterable(
              attackPatternRepository.findAllByExternalIdInIgnoreCase(
                  sourceContract.getAttackPatternsExternalIds()));
      contractDB.setAttackPatterns(attackPatterns);
    }

    // Update domains (merge) if not a payload-based injector
    if (!isPayloads) {
      Set<Domain> currentDomains = this.upserts(contractDB.getDomains());
      Set<Domain> domainsToAdd = this.upserts(sourceContract.getDomains());
      contractDB.setDomains(this.mergeDomains(currentDomains, domainsToAdd));
    }

    try {
      contractDB.setContent(mapper.writeValueAsString(sourceContract));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Failed to serialize contract content for: " + contractDB.getId(), e);
    }
  }

  private boolean shouldDeleteContract(InjectorContract contractDB, Injector injector) {
    return !contractDB.getCustom() && (!injector.isPayloads() || contractDB.getPayload() == null);
  }

  private void createNewInjector(
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
    newInjector.setName(name);
    newInjector.setType(contractor.getType());
    newInjector.setCategory(category);
    newInjector.setCustomContracts(isCustomizable);
    newInjector.setExecutorCommands(executorCommands);
    newInjector.setExecutorClearCommands(executorClearCommands);
    newInjector.setPayloads(isPayloads);
    newInjector.setDependencies(dependencies.toArray(new ExternalServiceDependency[0]));

    Injector savedInjector = injectorRepository.save(newInjector);

    List<InjectorContract> injectorContracts =
        staticContracts.stream()
            .map(contract -> createInjectorContract(contract, savedInjector, isPayloads))
            .toList();
    injectorContractRepository.saveAll(injectorContracts);
  }

  /**
   * Creates an InjectorContract from a Contract definition.
   *
   * @param contract the contract definition
   * @param injector the parent injector
   * @param isPayloads whether this is a payload-based injector
   * @return the created InjectorContract
   */
  private InjectorContract createInjectorContract(
      Contract contract, Injector injector, boolean isPayloads) {
    InjectorContract injectorContract = new InjectorContract();
    injectorContract.setId(contract.getId());
    injectorContract.setManual(contract.isManual());
    injectorContract.setAtomicTesting(contract.isAtomicTesting());
    injectorContract.setPlatforms(contract.getPlatforms().toArray(new PLATFORM_TYPE[0]));
    injectorContract.setNeedsExecutor(contract.isNeedsExecutor());

    Map<String, String> labels =
        contract.getLabel().entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
    injectorContract.setLabels(labels);
    injectorContract.setInjector(injector);

    if (!contract.getAttackPatternsExternalIds().isEmpty()) {
      List<AttackPattern> attackPatterns =
          fromIterable(
              attackPatternRepository.findAllByExternalIdInIgnoreCase(
                  contract.getAttackPatternsExternalIds()));
      injectorContract.setAttackPatterns(attackPatterns);
    } else {
      injectorContract.setAttackPatterns(new ArrayList<>());
    }

    try {
      injectorContract.setContent(mapper.writeValueAsString(contract));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    if (!isPayloads && contract.getDomains() != null) {
      injectorContract.setDomains(this.upserts(contract.getDomains()));
    }

    return injectorContract;
  }

  /**
   * Returns all registered injectors.
   *
   * @return an iterable of all injectors
   */
  public Iterable<Injector> injectors() {
    return injectorRepository.findAll();
  }

  /**
   * Upserts a set of domains, creating any that don't exist.
   *
   * @param domains the domains to upsert
   * @return the persisted domains
   */
  public Set<Domain> upserts(final Set<Domain> domains) {
    if (domains == null) {
      return new HashSet<>();
    }
    return domains.stream().map(this::upsert).collect(Collectors.toSet());
  }

  /**
   * Upserts a single domain, creating it if it doesn't exist.
   *
   * @param domain the domain to upsert
   * @return the persisted domain
   */
  private Domain upsert(final Domain domain) {
    if (domain == null || domain.getName() == null) {
      return null;
    }

    return domainRepository
        .findByName(domain.getName())
        .orElseGet(
            () ->
                domainRepository.save(
                    new Domain(
                        null,
                        domain.getName(),
                        domain.getColor() != null ? domain.getColor() : randomColor(),
                        Instant.now(),
                        null)));
  }

  /**
   * Generates a random hex color code.
   *
   * @return a random color in "#RRGGBB" format
   */
  private String randomColor() {
    return String.format("#%06x", ThreadLocalRandom.current().nextInt(0xffffff + 1));
  }

  /**
   * Merges two sets of domains, handling the "To classify" placeholder.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>If both sets are empty or only contain "To classify", returns a "To classify" domain
   *   <li>If only one set has real domains, those are returned
   *   <li>Otherwise, both sets are combined
   * </ul>
   *
   * @param existingDomains the current domains
   * @param domains the domains to merge in
   * @return the merged set of domains
   */
  public Set<Domain> mergeDomains(final Set<Domain> existingDomains, final Set<Domain> domains) {
    final boolean isExistingDomainsEmptyOrToClassify = isEmptyOrToClassify(existingDomains);
    final boolean domainsEmptyOrToClassify = isEmptyOrToClassify(domains);

    // Both empty or just "To classify" - return placeholder
    if (isExistingDomainsEmptyOrToClassify && domainsEmptyOrToClassify) {
      return new HashSet<>(
          Collections.singletonList(
              this.upsert(
                  new Domain(null, TO_CLASSIFY, DEFAULT_DOMAIN_COLOR, Instant.now(), null))));
    }

    // Filter out "To classify" from domains to add
    Set<Domain> domainsToAdd = domainsEmptyOrToClassify ? new HashSet<>() : domains;

    // If existing is empty, just return the new domains
    if (isExistingDomainsEmptyOrToClassify) {
      return domainsToAdd;
    }

    // Merge both sets
    return Stream.concat(existingDomains.stream(), domainsToAdd.stream())
        .collect(Collectors.toSet());
  }

  /**
   * Checks if a domain set is empty or contains only the "To classify" placeholder.
   *
   * @param domains the domains to check
   * @return true if empty or only contains "To classify"
   */
  private boolean isEmptyOrToClassify(final Set<Domain> domains) {
    if (domains == null || domains.isEmpty()) {
      return true;
    }
    return domains.size() == 1 && TO_CLASSIFY.equals(domains.iterator().next().getName());
  }

  /** Exception thrown when injector registration fails. */
  public static class InjectorRegistrationException extends Exception {
    public InjectorRegistrationException(String message) {
      super(message);
    }

    public InjectorRegistrationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
