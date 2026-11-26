package io.openaev;

import io.openaev.config.ProductionActiveTables;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared Spring context for the integration tests that name the production list of isolated tables
 * as their own. The test profile arms the same list ({@code TestProfileActiveTablesTest} fails when
 * the two differ), so a class that extends {@link IntegrationTest} directly runs on that list too;
 * this class registers it again from {@link ProductionActiveTables}, which reads the main
 * resources, so a class that extends it follows production even if the profile line drifts.
 *
 * <p>Spring keys its context cache on the {@code DynamicPropertySource} methods, by identity, so a
 * class that declares its own copy of this method builds, and holds, a context of its own even
 * though it registers the same value: every such class holds a full application context in the heap
 * of the test JVM until the cache evicts it. A class that extends this one declares nothing of its
 * own and resolves to the context of every other, as long as it adds no mock bean or property.
 *
 * <p>A test that arms a different list on purpose keeps its own {@code DynamicPropertySource} and
 * its own context, and is listed in {@code pinned-active-tables-baseline.txt}.
 */
public abstract class ProductionTablesIntegrationTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }
}
