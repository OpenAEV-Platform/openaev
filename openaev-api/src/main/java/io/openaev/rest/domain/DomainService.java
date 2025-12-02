package io.openaev.rest.domain;

import static io.openaev.database.specification.DomainSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.FilterUtilsJpa.PAGE_NUMBER_OPTION;
import static io.openaev.utils.FilterUtilsJpa.PAGE_SIZE_OPTION;
import static io.openaev.utils.StringUtils.generateRandomColor;

import io.openaev.database.model.Domain;
import io.openaev.database.repository.DomainRepository;
import io.openaev.rest.domain.enums.DefaultDomain;
import io.openaev.rest.domain.enums.DomainKeyWords;
import io.openaev.rest.domain.form.DomainBaseInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.FilterUtilsJpa;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  public Set<Domain> upserts(final Set<Domain> domains) {
    if (domains == null) {
        return Set.of();
    }

    return domains.stream().map(this::upsert).collect(Collectors.toSet());
  }

  public Domain upsert(final String name, final String color) {
    Optional<Domain> existingDomain = domainRepository.findByName(name);
    return existingDomain.orElseGet(
        () ->
            domainRepository.save(
                new Domain(
                    null,
                    name,
                    color != null ? color : generateRandomColor(),
                    Instant.now(),
                    null)));
  }

  public Set<Domain> mergeDomains(
      final Set<Domain> existingDomains, final Set<Domain> addedDomains) {
    if (existingDomains == null
        || existingDomains.isEmpty()
        || (existingDomains.size() == 1
            && DefaultDomain.TOCLASSIFY
                .getDomain()
                .getName()
                .equals(existingDomains.iterator().next().getName()))) {
      return addedDomains;
    }

    return Stream.concat(existingDomains.stream(), addedDomains.stream())
        .collect(Collectors.toSet());
  }

  public Set<Domain> findDomainByNameAndDescription(final String name, final String description) {
    Set<Domain> domains = new HashSet<>();

    if (findInKeywords(DomainKeyWords.ENDPOINT, name)
        || findInKeywords(DomainKeyWords.ENDPOINT, description)) {
      domains.add(DefaultDomain.ENDPOINT.getDomain());
    }
    if (findInKeywords(DomainKeyWords.NETWORK, name)
        || findInKeywords(DomainKeyWords.NETWORK, description)) {
      domains.add(DefaultDomain.NETWORK.getDomain());
    }
    if (findInKeywords(DomainKeyWords.WEB_APP, name)
        || findInKeywords(DomainKeyWords.WEB_APP, description)) {
      domains.add(DefaultDomain.WEB_APP.getDomain());
    }
    if (findInKeywords(DomainKeyWords.EMAIL_INFILTRATION, name)
        || findInKeywords(DomainKeyWords.EMAIL_INFILTRATION, description)) {
      domains.add(DefaultDomain.EMAIL_INFILTRATION.getDomain());
    }
    if (findInKeywords(DomainKeyWords.DATA_EXFILTRATION, name)
        || findInKeywords(DomainKeyWords.DATA_EXFILTRATION, description)) {
      domains.add(DefaultDomain.DATA_EXFILTRATION.getDomain());
    }
    if (findInKeywords(DomainKeyWords.URL_FILTERING, name)
        || findInKeywords(DomainKeyWords.URL_FILTERING, description)) {
      domains.add(DefaultDomain.URL_FILTERING.getDomain());
    }
    if (findInKeywords(DomainKeyWords.CLOUD, name)
        || findInKeywords(DomainKeyWords.CLOUD, description)) {
      domains.add(DefaultDomain.CLOUD.getDomain());
    }

    if (domains.isEmpty()) {
      domains.add(DefaultDomain.ENDPOINT.getDomain());
    }

    return domains;
  }

  private boolean findInKeywords(DomainKeyWords keywords, String searchValue) {
    return keywords.getKeywords().stream()
        .map(String::toLowerCase)
            .anyMatch(keyword -> searchValue.toLowerCase().contains(keyword));
  }

  private String randomColor() {
    Random rand = new Random();
    return String.format("#%06x", rand.nextInt(0xffffff + 1));
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
}
