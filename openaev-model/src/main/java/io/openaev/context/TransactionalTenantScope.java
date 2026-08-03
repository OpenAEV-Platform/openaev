package io.openaev.context;

import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionalTenantScope {

  private final EntityManager entityManager;

  public String currentScope() {
    return (String)
        entityManager
            .createNativeQuery("SELECT coalesce(current_setting('app.current_tenants', true), '')")
            .getSingleResult();
  }

  public List<String> currentTenantIds() {
    String scope = currentScope();
    if (scope == null || scope.isBlank()) {
      return List.of();
    }
    return Arrays.stream(scope.split(","))
        .map(String::trim)
        .filter(id -> !id.isEmpty())
        .distinct()
        .toList();
  }
}
