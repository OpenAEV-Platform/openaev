package io.openaev.config;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * Decides whether a null-tenant write is a misattribution for a given table, deriving the answer
 * from {@link TenantTables}, never by hand.
 *
 * <p>A strict table (its entity is a {@code TenantBase}) can never hold a platform row, so a null
 * tenant there is a lost attribution and is flagged. A dual-scope table (its entity is a {@code
 * DualScopeBase}) legitimately holds platform rows ({@code tenant_id IS NULL}), so a null there is
 * silent. The two lists come from the entity model ({@link TenantTables#fromEntities}), which is
 * the source of truth during the rollout: a strict entity often still has a nullable column, so
 * schema nullability alone would wrongly read it as dual-scope. A table with no entity (a join or
 * link table) falls back to schema nullability ({@link TenantFilteringConfig#deriveFromSchema},
 * {@code tenant_id NOT NULL} means strict), which is the same rule the inspector's own {@link
 * TenantTables} bean uses.
 */
final class WriteAttrTableClassifier {

  private final Set<String> entityStrict;
  private final Set<String> entityDual;
  private final Set<String> schemaStrict;

  WriteAttrTableClassifier(
      Set<String> entityStrict, Set<String> entityDual, Set<String> schemaStrict) {
    this.entityStrict = lower(entityStrict);
    this.entityDual = lower(entityDual);
    this.schemaStrict = lower(schemaStrict);
  }

  static WriteAttrTableClassifier from(EntityManagerFactory emf, DataSource dataSource) {
    Set<Class<?>> entities =
        emf.getMetamodel().getEntities().stream()
            .map(EntityType::getJavaType)
            .collect(Collectors.toSet());
    TenantTables model = TenantTables.fromEntities(entities);
    TenantTables schema = TenantFilteringConfig.deriveFromSchema(dataSource);
    return new WriteAttrTableClassifier(model.strict(), model.dualScope(), schema.strict());
  }

  /** True when a null tenant on this table is a misattribution rather than a platform row. */
  boolean flagsNullTenant(String table) {
    String name = table.toLowerCase(Locale.ROOT);
    if (entityDual.contains(name)) {
      return false;
    }
    if (entityStrict.contains(name)) {
      return true;
    }
    return schemaStrict.contains(name);
  }

  private static Set<String> lower(Set<String> names) {
    return names.stream()
        .map(n -> n.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }
}
