package io.openaev.engine.model.securitydomain;

import static io.openaev.engine.EsUtils.buildRestrictions;

import io.openaev.database.raw.RawDomain;
import io.openaev.database.repository.DomainRepository;
import io.openaev.engine.Handler;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SecurityDomainHandler implements Handler<EsSecurityDomain> {

  private DomainRepository domainRepository;

  @Autowired
  public void setDomainRepository(DomainRepository domainRepository) {
    this.domainRepository = domainRepository;
  }

  @Override
  public List<EsSecurityDomain> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawDomain> forIndexing = domainRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            domain -> {
              EsSecurityDomain esSecurityDomain = new EsSecurityDomain();
              // Base
              esSecurityDomain.setBase_id(domain.getDomain_id());
              esSecurityDomain.setBase_representative(domain.getDomain_name());
              esSecurityDomain.setBase_created_at(domain.getDomain_created_at());
              esSecurityDomain.setBase_updated_at(domain.getDomain_updated_at());

              esSecurityDomain.setBase_restrictions(buildRestrictions(domain.getDomain_id()));

              esSecurityDomain.setDomain_color(domain.getDomain_color());
              return esSecurityDomain;
            })
        .toList();
  }
}
