package io.openaev.architecture.fixture;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.FilterDefs;
import org.hibernate.annotations.FilterJoinTable;
import org.hibernate.annotations.FilterJoinTables;
import org.hibernate.annotations.Filters;
import org.hibernate.annotations.ParamDef;
import org.hibernate.engine.spi.SessionImplementor;

/**
 * Every shape of Hibernate filter use the tenant filter guard must find, and the code it must leave
 * alone. Nothing here runs: the scan reads the class files only, and no shape carries
 * {@code @Entity}.
 */
public final class TenantFilterRemovedFixtures {

  private TenantFilterRemovedFixtures() {}

  // -- ANNOTATIONS --

  @Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
  public static class DeclaresAFilter {}

  @FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
  public static class DeclaresAFilterDefinition {}

  /** Empty containers carry no nested filter, so each container name is proven on its own. */
  @Filters({})
  public static class DeclaresAFilterContainer {}

  @FilterDefs({})
  public static class DeclaresAFilterDefinitionContainer {}

  public static class DeclaresAFilterOnAJoinTable {
    @FilterJoinTable(name = "tenantFilter", condition = "tenant_id = :tenantId")
    public Object links;
  }

  public static class DeclaresAJoinTableFilterContainer {
    @FilterJoinTables({})
    public Object links;
  }

  public static class DeclaresNoFilter {}

  // -- CALLS --

  public static class EnablesAFilter {
    public void enable(EntityManager entityManager) {
      entityManager.unwrap(Session.class).enableFilter("tenantFilter");
    }
  }

  public static class EnablesAFilterThroughAMethodReference {
    public java.util.function.Function<String, org.hibernate.Filter> enabler(Session session) {
      return session::enableFilter;
    }
  }

  public static class DisablesAFilterThroughASubtype {
    public void disable(EntityManager entityManager) {
      entityManager.unwrap(SessionImplementor.class).disableFilter("tenantFilter");
    }
  }

  public static class ReadsAFilterOnAStatelessSession {
    public Object read(StatelessSession session) {
      return session.getEnabledFilter("tenantFilter");
    }
  }

  public static class ReadsTheDefinedFilterNamesOfAFactory {
    public Object read(SessionFactory factory) {
      return factory.getDefinedFilterNames();
    }
  }

  public static class UsesTheSessionWithoutFilters {
    public void flush(EntityManager entityManager) {
      entityManager.unwrap(Session.class).flush();
    }
  }
}
