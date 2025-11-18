package io.openaev.rest.domain;

import io.openaev.database.model.Domain;
import io.openaev.database.repository.DomainRepository;
import io.openaev.rest.domain.form.DomainCreateInput;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainService {

  private static final String DOMAIN_NOT_FOUND_MSG = "Domain not found with name";

  private final DomainRepository domainRepository;

  public List<Domain> searchDomains() {
    return (List<Domain>) domainRepository.findAll();
  }

  private Optional<Domain> findByName(final String name) {
    return domainRepository.findByName(name);
  }

  public Domain findById(final String domainId) {
    return domainRepository
        .findById(domainId)
        .orElseThrow(() -> new ElementNotFoundException(DOMAIN_NOT_FOUND_MSG + domainId));
  }

  public Domain upsertDomain(final DomainCreateInput input) {
    Optional<Domain> existingDomain = this.findByName(input.getName());

    if (existingDomain.isPresent()) {
      return existingDomain.get();
    } else {
      Domain domain = new Domain();
      domain.setName(input.getName());
      domain.setColor(randomColor());
      if (input.getColor() != null) {
        domain.setColor(input.getColor());
      }
      return domainRepository.save(domain);
    }
  }

  private String randomColor() {
    Random rand = new Random();
    return String.format("#%06x", rand.nextInt(0xffffff + 1));
  }
}
