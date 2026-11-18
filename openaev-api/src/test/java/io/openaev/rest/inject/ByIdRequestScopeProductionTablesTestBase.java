package io.openaev.rest.inject;

import io.openaev.config.ProductionActiveTables;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Shared Spring context for the by-id request-scope suites that run with the unmodified production
 * list of isolated tables: {@link ByIdRequestScopeTest} declares no configuration of its own, so it
 * resolves to this one context. {@link ByIdRequestScopeArmedTest} arms {@code injects} on top and
 * keeps its own.
 */
abstract class ByIdRequestScopeProductionTablesTestBase extends ByIdRequestScopeTestBase {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }
}
