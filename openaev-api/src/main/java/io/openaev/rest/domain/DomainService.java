package io.openaev.rest.domain;

import static io.openaev.database.specification.DomainSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.FilterUtilsJpa.PAGE_NUMBER_OPTION;
import static io.openaev.utils.FilterUtilsJpa.PAGE_SIZE_OPTION;
import static io.openaev.utils.StringUtils.generateRandomColor;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DomainRepository;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.rest.domain.form.DomainBaseInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.form.InjectorContractDomainDTO;
import io.openaev.utils.FilterUtilsJpa;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DomainService implements DependenciesManager {

  private static final String DOMAIN_ID_NOT_FOUND_MSG = "Domain not found with id";

  private final DomainRepository domainRepository;

  /** Default domain name for uncategorized contracts. */
  private static final String TO_CLASSIFY = "To classify";

  /** Default color for new domains. */
  private static final String DEFAULT_DOMAIN_COLOR = "#FFFFFF";

  /**
   * High 32 bits of the domain creation advisory-lock key, so it never collides with another
   * advisory lock of the platform ("DO" = domain).
   */
  private static final long CREATION_LOCK_NAMESPACE = 0x444F_0001L;

  public List<Domain> searchDomains() {
    return fromIterable(domainRepository.findAll());
  }

  public Optional<Domain> findOptionalById(final String domainId) {
    return domainRepository.findById(domainId);
  }

  public Optional<Domain> findOptionalByName(final String name) {
    return domainRepository.findByName(name);
  }

  public Optional<Domain> findOptionalByName(final String name, final String tenantId) {
    return domainRepository.findByNameAndTenantId(name, tenantId);
  }

  public Domain findById(final String domainId) {
    return domainRepository
        .findById(domainId)
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    (String.format("%s: %s", DOMAIN_ID_NOT_FOUND_MSG, domainId))));
  }

  public Iterable<Domain> findAllById(final List<String> domainIds) {
    return domainRepository.findAllById(domainIds);
  }

  @Transactional
  public Domain upsert(final DomainBaseInput input, final String tenantId) {
    return doUpsert(input.getName(), input.getColor(), new Tenant(tenantId));
  }

  @Transactional
  public Domain upsert(final DomainBaseInput input) {
    return doUpsert(
        input.getName(), input.getColor(), new Tenant(TenantContext.getCurrentTenant()));
  }

  @Transactional
  public Domain upsert(final Domain domainToUpsert) {
    return doUpsert(
        domainToUpsert.getName(), domainToUpsert.getColor(), domainToUpsert.getTenant());
  }

  /**
   * Saves a collection of contract entities in the database. If an entity already exists (searched
   * by name) in the database, it is retrieved instead of saved (no modification). If the entity is
   * not found, it is created.
   *
   * @param domains set of domain entities to save or retrieve.
   * @return set of saved or retrieved domains
   */
  @Transactional
  public Set<Domain> upsertDomainEntities(Set<Domain> domains, String tenantId) {
    return this.upserts(
        Optional.ofNullable(domains)
            .map(
                collection ->
                    collection.stream().map(InjectorContractDomainDTO::fromDomain).collect(toSet()))
            .orElse(null),
        tenantId);
  }

  /**
   * Saves a collection of contract DTOs in the database. If an entity already exists (searched by
   * name, then by {@link PresetDomain#toMatchingKey matching key}) in the database, it is retrieved
   * instead of saved (no modification). If the entity is not found, it is created, under the {@link
   * #creationLockKey creation lock} of its matching key.
   *
   * @param domains set of domain DTOs to save or retrieve.
   * @return set of saved or retrieved domains
   */
  @Transactional
  public Set<Domain> upserts(Set<InjectorContractDomainDTO> domains, String tenantId) {
    if (domains == null || domains.isEmpty()) {
      return new HashSet<>();
    }

    Set<String> names = domains.stream().map(InjectorContractDomainDTO::getName).collect(toSet());
    Map<String, Domain> resolved = findExisting(names, tenantId);
    Set<String> missing = new HashSet<>(names);
    missing.removeAll(resolved.keySet());
    if (!missing.isEmpty()) {
      // Sorted so that two batches locking overlapping keys always take them in the same order.
      missing.stream()
          .map(name -> creationLockKey(tenantId, name))
          .distinct()
          .sorted()
          .forEach(domainRepository::lockDomainCreation);
      resolved.putAll(findExisting(missing, tenantId));
    }

    List<Domain> created = new ArrayList<>();
    return domains.stream()
        .map(
            d ->
                resolved.computeIfAbsent(
                    d.getName(),
                    name ->
                        findByMatchingKey(name, created)
                            .orElseGet(
                                () -> {
                                  Domain domain =
                                      domainRepository.save(
                                          buildSanityDomain(
                                              name, d.getColor(), new Tenant(tenantId)));
                                  created.add(domain);
                                  return domain;
                                })))
        .collect(toSet());
  }

  /**
   * Returns the tenant's domain with this name, or with the same {@link PresetDomain#toMatchingKey
   * matching key} when no name matches exactly, and creates it only when neither exists. A starter
   * pack or an injector carrying a legacy spelling such as "Table-Top" thereby reuses the
   * "Tabletop" preset instead of creating a duplicate. The creation runs under the {@link
   * #creationLockKey creation lock} of the matching key and looks the domain up again once the lock
   * is held, so concurrent upserts of two spellings of a new name create it only once.
   *
   * @param name the domain name
   * @param color the color of the domain when it has to be created (random when null)
   * @param tenant the tenant owning the domain
   * @return the existing or newly created domain
   */
  @Transactional
  public Domain upsert(final String name, final String color, final Tenant tenant) {
    return doUpsert(name, color, tenant);
  }

  /**
   * Body of the {@code upsert} overloads, kept un-annotated so they share it without an intra-class
   * call to a {@code @Transactional} method, which would bypass the Spring proxy. Must run inside a
   * transaction: the creation lock is transaction-scoped.
   */
  private Domain doUpsert(final String name, final String color, final Tenant tenant) {
    return findExisting(name, tenant.getId())
        .orElseGet(
            () -> {
              domainRepository.lockDomainCreation(creationLockKey(tenant.getId(), name));
              return findExisting(name, tenant.getId())
                  .orElseGet(() -> domainRepository.save(buildSanityDomain(name, color, tenant)));
            });
  }

  private Optional<Domain> findExisting(final String name, final String tenantId) {
    return domainRepository
        .findByNameAndTenantId(name, tenantId)
        .or(() -> findByMatchingKey(name, domainRepository.findByTenantId(tenantId)));
  }

  /**
   * Resolves names to existing domains of the tenant, by exact name, then by {@link
   * PresetDomain#toMatchingKey matching key}. The tenant's domains are only loaded when a name has
   * no exact match.
   *
   * @return the resolved domains by requested name, without the names nothing matches
   */
  private Map<String, Domain> findExisting(final Set<String> names, final String tenantId) {
    Map<String, Domain> resolved =
        domainRepository.findByNameInAndTenantId(names, tenantId).stream()
            .collect(
                toMap(
                    Domain::getName, Function.identity(), (first, second) -> first, HashMap::new));
    if (!resolved.keySet().containsAll(names)) {
      List<Domain> tenantDomains = domainRepository.findByTenantId(tenantId);
      names.stream()
          .filter(name -> !resolved.containsKey(name))
          .forEach(
              name ->
                  findByMatchingKey(name, tenantDomains)
                      .ifPresent(domain -> resolved.put(name, domain)));
    }
    return resolved;
  }

  /**
   * Key of the transaction-scoped advisory lock serialising the creation of a domain: the fixed
   * {@link #CREATION_LOCK_NAMESPACE} in the high 32 bits and the hash of the tenant and {@link
   * PresetDomain#toMatchingKey matching key} in the low 32 bits. Two spellings of a name share the
   * key; a hash collision only serialises two unrelated creations.
   */
  static long creationLockKey(final String tenantId, final String name) {
    String scope = tenantId + ":" + PresetDomain.toMatchingKey(name);
    return (CREATION_LOCK_NAMESPACE << 32) | (scope.hashCode() & 0xFFFF_FFFFL);
  }

  /**
   * Picks among candidate domains the one whose name has the same {@link PresetDomain#toMatchingKey
   * matching key} as the given name. A preset wins over a custom domain sharing its key, then names
   * are compared alphabetically, so the choice is deterministic.
   *
   * @param name the domain name to resolve
   * @param candidates the domains of the tenant
   * @return the matching domain, empty when none matches or the name has an empty key
   */
  static Optional<Domain> findByMatchingKey(
      final String name, final Collection<Domain> candidates) {
    String key = PresetDomain.toMatchingKey(name);
    if (key.isEmpty()) {
      return Optional.empty();
    }
    return candidates.stream()
        .filter(candidate -> key.equals(PresetDomain.toMatchingKey(candidate.getName())))
        .min(
            Comparator.comparing(
                    (Domain candidate) -> !PresetDomain.isPresetName(candidate.getName()))
                .thenComparing(Domain::getName));
  }

  @Transactional
  public Set<Domain> mergeDomains(
      final Set<Domain> existingDomains, final Set<Domain> addedDomains, final Tenant tenant) {
    final boolean isExistingDomainsEmptyOrToClassify = isEmptyOrToClassify(existingDomains);
    final boolean domainsEmptyOrToClassify = isEmptyOrToClassify(addedDomains);

    // Both empty or just "To classify" - return placeholder
    if (isExistingDomainsEmptyOrToClassify && domainsEmptyOrToClassify) {
      return new HashSet<>(
          Collections.singletonList(
              this.upsert(
                  Domain.builder()
                      .name(PresetDomain.getToClassify().getName())
                      .color(PresetDomain.getToClassify().getColor())
                      .tenant(tenant)
                      .build())));
    }

    // Filter out "To classify" from domains to add
    Set<Domain> domainsToAdd = domainsEmptyOrToClassify ? new HashSet<>() : addedDomains;

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

  public Set<Domain> findDomainByNameAndDescription(final String name) {
    Set<Domain> domains = new HashSet<>();
    domains.add(PresetDomain.getEndpoint());
    domains.addAll(PresetDomain.getRelevantDomainsFromKeywords(name));
    return domains;
  }

  // -- OPTION --

  public List<FilterUtilsJpa.Option> findAllAsOptionsByName(final String searchText) {
    Pageable pageable =
        PageRequest.of(PAGE_NUMBER_OPTION, PAGE_SIZE_OPTION, Sort.by(Sort.Direction.ASC, "name"));
    return fromIterable(domainRepository.findAll(byName(searchText), pageable)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  public List<FilterUtilsJpa.Option> findAllAsOptionsById(final List<String> ids) {
    return fromIterable(domainRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  // -- PRIVATE --

  private Domain buildSanityDomain(final String name, final String color, final Tenant tenant) {
    return Domain.builder()
        .name(name)
        .color(color != null ? color : generateRandomColor())
        .tenant(tenant)
        .build();
  }

  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    log.info("Tenant {} created — domains inserted", tenant.getId());
    try {
      domainRepository.saveAll(PresetDomain.getDomainsForTenant(tenant));
    } catch (Exception e) {
      throw new DependenciesManagerException(
          "Failed to insert domains for tenant " + tenant.getId(), e);
    }
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    // Automatic thanks to cascade delete
    log.info("Deleting all domains for tenant");
  }
}
