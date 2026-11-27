package io.openaev.config;

import io.openaev.aop.AccessControl;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathGraphVersion;
import io.openaev.database.repository.attackpath.AttackPathGraphVersionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A deliberately wrong writer for the write-attribution detector tests: it stamps the row with the
 * ambient tenant, which is the default tenant on the non-prefixed route, while the request scope is
 * the tenant the caller selected. It saves a transient entity with an assigned id, the shape that
 * reaches Hibernate as a merge.
 *
 * <p>The row is an {@code attackpath_graph_version} counter because that table is self-isolated
 * ({@code TenantTables#selfIsolatedTables}): the statement inspector never gates it, so nothing
 * refuses the wrong-tenant insert before the detector sees it. On an active table the statement
 * inspector sends the written tenant through {@code require_tenant_write}, which refuses such a
 * write, so the detector would never get to judge it.
 */
@RestController
@RequiredArgsConstructor
public class AmbientTenantGraphVersionWriter {

  public static final String URI = "/api/test/write-attribution/ambient-graph-versions";
  public static final String CONTROLLER =
      AmbientTenantGraphVersionWriter.class.getName() + ".create";
  public static final String TABLE = "attackpath_graph_version";

  private final AttackPathGraphVersionRepository versionRepository;

  @PostMapping(URI)
  @AccessControl(skipRBAC = true)
  @Transactional
  public String create(TxCtx ctx) {
    AttackPathGraphVersion version = new AttackPathGraphVersion();
    version.setId(UUID.randomUUID().toString());
    version.setValue(1L);
    version.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    return versionRepository.save(version).getId();
  }

  @TestConfiguration
  public static class Config {
    @Bean
    AmbientTenantGraphVersionWriter ambientTenantGraphVersionWriter(
        AttackPathGraphVersionRepository versionRepository) {
      return new AmbientTenantGraphVersionWriter(versionRepository);
    }
  }
}
