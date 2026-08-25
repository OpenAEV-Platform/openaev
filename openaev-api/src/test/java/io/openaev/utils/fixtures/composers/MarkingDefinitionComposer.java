package io.openaev.utils.fixtures.composers;

import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MarkingDefinitionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@code marking_definitions} is tenant-isolated on v2, which means there is no {@code
 * TenantBaseListener} to stamp {@code tenant_id} on persist — yet the column is {@code NOT NULL}. A
 * caller MUST therefore pin a tenant via {@link Composer#withTenantId} (or {@link
 * Composer#withTenant}) before {@link Composer#persist()}, otherwise the insert fails on the FK.
 */
@Component
public class MarkingDefinitionComposer extends ComposerBase<MarkingDefinition> {

  @Autowired private MarkingDefinitionRepository markingDefinitionRepository;
  @PersistenceContext private EntityManager entityManager;

  public class Composer extends InnerComposerBase<MarkingDefinition> {

    private final MarkingDefinition markingDefinition;

    public Composer(MarkingDefinition markingDefinition) {
      this.markingDefinition = markingDefinition;
    }

    public Composer withId(String id) {
      this.markingDefinition.setId(id);
      return this;
    }

    public Composer withTenant(Tenant tenant) {
      this.markingDefinition.setTenant(tenant);
      return this;
    }

    /** Reference-only load: no select is issued for a tenant we only need the FK of. */
    public Composer withTenantId(String tenantId) {
      return withTenant(entityManager.getReference(Tenant.class, tenantId));
    }

    public Composer withName(String name) {
      this.markingDefinition.setName(name);
      return this;
    }

    public Composer withType(String type) {
      this.markingDefinition.setType(type);
      return this;
    }

    public Composer withOrder(Integer order) {
      this.markingDefinition.setOrder(order);
      return this;
    }

    public Composer withColor(String color) {
      this.markingDefinition.setColor(color);
      return this;
    }

    @Override
    public Composer persist() {
      markingDefinitionRepository.save(this.markingDefinition);
      return this;
    }

    @Override
    public Composer delete() {
      markingDefinitionRepository.delete(this.markingDefinition);
      return this;
    }

    @Override
    public MarkingDefinition get() {
      return this.markingDefinition;
    }
  }

  public Composer forMarkingDefinition(MarkingDefinition markingDefinition) {
    generatedItems.add(markingDefinition);
    return new Composer(markingDefinition);
  }
}
