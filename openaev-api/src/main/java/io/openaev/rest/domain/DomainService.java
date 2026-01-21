package io.openaev.rest.domain;

import static io.openaev.database.specification.DomainSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.FilterUtilsJpa.PAGE_NUMBER_OPTION;
import static io.openaev.utils.FilterUtilsJpa.PAGE_SIZE_OPTION;
import static io.openaev.utils.StringUtils.generateRandomColor;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import io.openaev.database.model.Domain;
import io.openaev.database.repository.DomainRepository;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.rest.domain.form.DomainBaseInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.FilterUtilsJpa;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DomainService {

  private static final String DOMAIN_ID_NOT_FOUND_MSG = "Domain not found with id";
  private static final String DOMAIN_NAME_NOT_FOUND_MSG = "Domain not found with name";

  private final DomainRepository domainRepository;

  /** Default domain name for uncategorized contracts. */
  private static final String TO_CLASSIFY = "To classify";

  /** Default color for new domains. */
  private static final String DEFAULT_DOMAIN_COLOR = "#FFFFFF";

  public List<Domain> searchDomains() {
    return fromIterable(domainRepository.findAll());
  }

  private Optional<Domain> findByName(final String name) {
    return Optional.ofNullable(
        domainRepository
            .findByName(name)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        (String.format("%s: %s", DOMAIN_NAME_NOT_FOUND_MSG, name)))));
  }

  public Optional<Domain> findOptionalById(final String domainId) {
    return domainRepository.findById(domainId);
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
  public Domain upsert(final DomainBaseInput input) {
    return this.upsert(input.getName(), input.getColor());
  }

  @Transactional
  public Domain upsert(final Domain domainToUpsert) {
    return this.upsert(domainToUpsert.getName(), domainToUpsert.getColor());
  }

  @Transactional
  public Set<Domain> upserts(Set<Domain> domains) {
    if (domains == null || domains.isEmpty()) {
      return new HashSet<>();
    }

    Map<String, Domain> existing =
        domainRepository
            .findByNameIn(domains.stream().map(Domain::getName).collect(toSet()))
            .stream()
            .collect(toMap(Domain::getName, Function.identity()));

    return domains.stream()
        .map(
            d ->
                existing.computeIfAbsent(
                    d.getName(),
                    name -> domainRepository.save(buildSanityDomain(name, d.getColor()))))
        .collect(toSet());
  }

  public Domain upsert(final String name, final String color) {
    Optional<Domain> existingDomain = domainRepository.findByName(name);
    return existingDomain.orElseGet(() -> domainRepository.save(buildSanityDomain(name, color)));
  }

  @Transactional
  public Set<Domain> mergeDomains(
      final Set<Domain> existingDomains, final Set<Domain> addedDomains) {
    final boolean isExistingDomainsEmptyOrToClassify = isEmptyOrToClassify(existingDomains);
    final boolean domainsEmptyOrToClassify = isEmptyOrToClassify(addedDomains);

    // Both empty or just "To classify" - return placeholder
    if (isExistingDomainsEmptyOrToClassify && domainsEmptyOrToClassify) {
      return new HashSet<>(
          Collections.singletonList(
              this.upsert(
                  new Domain(null, TO_CLASSIFY, DEFAULT_DOMAIN_COLOR, Instant.now(), null))));
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
    domains.add(PresetDomain.ENDPOINT);
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

  private Domain buildSanityDomain(final String name, final String color) {
    return new Domain(
        null, name, color != null ? color : generateRandomColor(), Instant.now(), null);
  }
}
