package io.openaev.config;

import io.openaev.aop.AccessControl;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.RoleRepository;
import java.util.Set;
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
 */
@RestController
@RequiredArgsConstructor
public class AmbientTenantRoleWriter {

  public static final String URI = "/api/test/write-attribution/ambient-roles";
  public static final String CONTROLLER = AmbientTenantRoleWriter.class.getName() + ".create";

  private final RoleRepository roleRepository;

  @PostMapping(URI)
  @AccessControl(skipRBAC = true)
  @Transactional
  public String create(TxCtx ctx) {
    Role role = new Role();
    role.setId(UUID.randomUUID().toString());
    role.setName("wattr-ambient");
    role.setDescription("wattr-ambient");
    role.setCapabilities(Set.of());
    role.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    return roleRepository.save(role).getId();
  }

  @TestConfiguration
  public static class Config {
    @Bean
    AmbientTenantRoleWriter ambientTenantRoleWriter(RoleRepository roleRepository) {
      return new AmbientTenantRoleWriter(roleRepository);
    }
  }
}
