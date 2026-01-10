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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InjectorService {

  private static final String TO_CLASSIFY = "To classify";

  private final ObjectMapper mapper;
  private final FileService fileService;
  private final InjectorRepository injectorRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final DomainRepository domainRepository;

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
      throws Exception {
    if (!contractor.isExpose()) {
      Injector injector = injectorRepository.findById(id).orElse(null);
      if (injector != null) {
        injectorRepository.deleteById(id);
        return;
      }
      return;
    }
    if (contractor.getIcon() != null) {
      InputStream iconData = contractor.getIcon().getData();
      fileService.uploadStream(INJECTORS_IMAGES_BASE_PATH, contractor.getType() + ".png", iconData);
    }
    // We need to support upsert for registration
    Injector injector = injectorRepository.findById(id).orElse(null);
    if (injector == null) {
      Injector injectorChecking = injectorRepository.findByType(contractor.getType()).orElse(null);
      if (injectorChecking != null) {
        throw new Exception(
            "The injector "
                + contractor.getType()
                + " already exists with a different ID, please delete it or contact your administrator.");
      }
    }
    // Check error to avoid changing ID
    List<Contract> staticContracts = contractor.contracts();
    if (injector != null) {
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
      List<String> existing = new ArrayList<>();
      List<InjectorContract> toUpdates = new ArrayList<>();
      List<String> toDeletes = new ArrayList<>();
      injector
          .getContracts()
          .forEach(
              contractDB -> {
                // Find matching static contract for this DB contract
                Optional<Contract> current =
                    staticContracts.stream()
                        .filter(contract -> contract.getId().equals(contractDB.getId()))
                        .findFirst();
                if (current.isPresent()) {
                  existing.add(contractDB.getId());
                  contractDB.setManual(current.get().isManual());
                  contractDB.setAtomicTesting(current.get().isAtomicTesting());
                  contractDB.setPlatforms(
                      current.get().getPlatforms().toArray(new PLATFORM_TYPE[0]));
                  contractDB.setNeedsExecutor(current.get().isNeedsExecutor());
                  Map<String, String> labels =
                      current.get().getLabel().entrySet().stream()
                          .collect(
                              Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
                  contractDB.setLabels(labels);
                  // If no override of TTPs, retrieve those of the contract
                  if (contractDB.getAttackPatterns().isEmpty()) {
                    if (!current.get().getAttackPatternsExternalIds().isEmpty()) {
                      List<AttackPattern> attackPatterns =
                          fromIterable(
                              attackPatternRepository.findAllByExternalIdInIgnoreCase(
                                  current.get().getAttackPatternsExternalIds()));
                      contractDB.setAttackPatterns(attackPatterns);
                    }
                  }
                  // Manage the update for domains by doing a merge, if payloads, domains will be
                  // hold by payloads
                  if (!isPayloads) {
                    Set<Domain> currentDomains = this.upserts(contractDB.getDomains());
                    Set<Domain> domainsToAdd = this.upserts(current.get().getDomains());
                    contractDB.setDomains(this.mergeDomains(currentDomains, domainsToAdd));
                  }
                  try {
                    contractDB.setContent(mapper.writeValueAsString(current.get()));
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                  toUpdates.add(contractDB);
                } else if (!contractDB.getCustom()
                    && (!injector.isPayloads() || contractDB.getPayload() == null)) {
                  // Delete non-custom contracts that no longer exist in static contracts
                  toDeletes.add(contractDB.getId());
                }
              });
      List<InjectorContract> toCreates =
          staticContracts.stream()
              .filter(c -> !existing.contains(c.getId()))
              .map(contract -> createInjectorContract(contract, injector, isPayloads))
              .toList();
      injectorContractRepository.deleteAllById(toDeletes);
      injectorContractRepository.saveAll(toCreates);
      injectorContractRepository.saveAll(toUpdates);
      injectorRepository.save(injector);
    } else {
      // save the injector
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
      // Save the contracts
      List<InjectorContract> injectorContracts =
          staticContracts.stream()
              .map(contract -> createInjectorContract(contract, savedInjector, isPayloads))
              .toList();
      injectorContractRepository.saveAll(injectorContracts);
    }
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

  public Iterable<Injector> injectors() {
    return injectorRepository.findAll();
  }

  public Set<Domain> upserts(final Set<Domain> domains) {
    return domains.stream().map(this::upsert).collect(Collectors.toSet());
  }

  private Domain upsert(final Domain domain) {
    Optional<Domain> existingDomain = domainRepository.findByName(domain.getName());
    return existingDomain.orElseGet(
        () ->
            domainRepository.save(
                new Domain(
                    null,
                    domain.getName(),
                    domain.getColor() != null ? domain.getColor() : randomColor(),
                    Instant.now(),
                    null)));
  }

  private String randomColor() {
    return String.format("#%06x", ThreadLocalRandom.current().nextInt(0xffffff + 1));
  }

  public Set<Domain> mergeDomains(final Set<Domain> existingDomains, final Set<Domain> domains) {
    final boolean isExistingDomainsEmptyOrToClassify = isEmptyOrToClassify(existingDomains);
    final boolean domainsEmptyOrToClassify = isEmptyOrToClassify(domains);

    if (isExistingDomainsEmptyOrToClassify && domainsEmptyOrToClassify) {
      return new HashSet<>(
          Collections.singletonList(
              this.upsert(new Domain(null, TO_CLASSIFY, "#FFFFFF", Instant.now(), null))));
    }

    Set<Domain> domainsToAdd = domains;
    if (domainsEmptyOrToClassify) {
      domainsToAdd = new HashSet<>();
    }

    if (isExistingDomainsEmptyOrToClassify) {
      return domainsToAdd;
    }

    return Stream.concat(existingDomains.stream(), domainsToAdd.stream())
        .collect(Collectors.toSet());
  }

  private boolean isEmptyOrToClassify(final Set<Domain> domains) {
    return domains == null
        || domains.isEmpty()
        || (domains.size() == 1 && TO_CLASSIFY.equals(domains.iterator().next().getName()));
  }
}
