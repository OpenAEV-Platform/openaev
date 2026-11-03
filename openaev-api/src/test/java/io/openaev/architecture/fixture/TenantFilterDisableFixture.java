package io.openaev.architecture.fixture;

import jakarta.persistence.EntityManager;
import java.util.function.Supplier;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.engine.spi.SessionImplementor;

/** Disables the v1 tenant filter in each shape the disableFilter guard must recognise. */
public class TenantFilterDisableFixture {

  private final EntityManager entityManager;

  public TenantFilterDisableFixture(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public void widenRead() {
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
  }

  public void widenThroughASubtype() {
    entityManager.unwrap(SessionImplementor.class).disableFilter("tenantFilter");
  }

  public void widenThroughAStatelessSession(StatelessSession statelessSession) {
    statelessSession.disableFilter("tenantFilter");
  }

  public Supplier<Object> widenInsideALambda() {
    return () -> {
      entityManager.unwrap(Session.class).disableFilter("tenantFilter");
      return null;
    };
  }

  public void widenTwice() {
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
  }

  public void armOnly() {
    entityManager.unwrap(Session.class).enableFilter("tenantFilter");
  }
}
