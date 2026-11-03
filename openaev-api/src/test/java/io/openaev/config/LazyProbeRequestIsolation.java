package io.openaev.config;

import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import java.util.function.Supplier;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.core.Ordered;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Starts every MockMvc request with an empty persistence context, as production does, while the
 * lazy-after-commit probe is on.
 *
 * <p>A {@code @Transactional} test shares one persistence context with the requests it makes, so an
 * entity the test created or loaded is the very instance the endpoint returns, its collections
 * already in memory. Jackson then writes them without a statement and neither the probe nor the
 * response shows the loss production has, where each request opens its own context and reads
 * committed rows. Flushing first keeps the test's pending writes; clearing then makes the request
 * load what it returns, the way it does in production.
 *
 * <p>Ordered first, so the flush runs under the scope the test's own writes were made in, before
 * the request filter that resets it.
 */
final class LazyProbeRequestIsolation implements MockMvcBuilderCustomizer, Ordered {

  private final Supplier<EntityManager> entityManager;

  LazyProbeRequestIsolation(Supplier<EntityManager> entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public void customize(ConfigurableMockMvcBuilder<?> builder) {
    Filter isolate =
        (request, response, chain) -> {
          if (TransactionSynchronizationManager.isActualTransactionActive()) {
            EntityManager current = entityManager.get();
            current.flush();
            current.clear();
          }
          chain.doFilter(request, response);
        };
    builder.addFilters(isolate);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
