package io.openaev.utils;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * JUnit 5 extension that disables RLS before a test and restores it after.
 *
 * <p>Executes {@code RESET ROLE} on the current connection to switch back to the superuser (which
 * bypasses RLS), then restores {@code SET ROLE openaev_app} after the test.
 *
 * <p>This works even in {@code @Transactional} tests because it operates on the already-obtained
 * connection via native SQL.
 */
public class RlsToggleExtension implements BeforeEachCallback, AfterEachCallback {

  @Override
  public void beforeEach(ExtensionContext context) {
    EntityManager em = getEntityManager(context);
    em.createNativeQuery("RESET ROLE").executeUpdate();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    EntityManager em = getEntityManager(context);
    em.createNativeQuery("SET ROLE openaev_app").executeUpdate();
  }

  private EntityManager getEntityManager(ExtensionContext context) {
    return SpringExtension.getApplicationContext(context).getBean(EntityManager.class);
  }
}
