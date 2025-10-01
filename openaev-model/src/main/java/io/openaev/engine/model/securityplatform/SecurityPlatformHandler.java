package io.openaev.engine.model.securityplatform;

import static io.openaev.engine.EsUtils.buildRestrictions;

import io.openaev.database.raw.RawAsset;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.engine.Handler;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class SecurityPlatformHandler implements Handler<EsSecurityPlatform> {

  private final SecurityPlatformRepository securityPlatformRepository;

  @Override
  public List<EsSecurityPlatform> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawAsset> forIndexing = securityPlatformRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            securityPlatform -> {
              EsSecurityPlatform esSecurityPlatform = new EsSecurityPlatform();
              // Base
              esSecurityPlatform.setBase_id(securityPlatform.getAsset_id());
              esSecurityPlatform.setName(securityPlatform.getAsset_name());
              esSecurityPlatform.setBase_created_at(securityPlatform.getAsset_created_at());
              esSecurityPlatform.setBase_updated_at(securityPlatform.getAsset_updated_at());
              esSecurityPlatform.setBase_representative(securityPlatform.getAsset_name());
              esSecurityPlatform.setBase_restrictions(
                  buildRestrictions(securityPlatform.getAsset_id()));
              // Specific
              return esSecurityPlatform;
            })
        .toList();
  }
}
