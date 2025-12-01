package io.openaev.rest.domain;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.StringUtils.generateRandomColor;

import io.openaev.database.model.Domain;
import io.openaev.database.repository.DomainRepository;
import io.openaev.rest.domain.form.DomainBaseInput;
import io.openaev.rest.exception.ElementNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
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

  public Domain upsertDomain(final DomainBaseInput input) {
    return this.upsert(input.getName(), input.getColor());
  }

  public Domain upsert(final Domain domainToUpsert) {
    return this.upsert(domainToUpsert.getName(), domainToUpsert.getColor());
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

  public Set<Domain> upserts(final Set<Domain> domains) {
    return domains.stream().map(this::upsert).collect(Collectors.toSet());
  }
}
