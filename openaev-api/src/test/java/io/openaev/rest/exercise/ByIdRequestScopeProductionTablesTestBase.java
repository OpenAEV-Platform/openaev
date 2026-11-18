package io.openaev.rest.exercise;

import io.openaev.config.ProductionActiveTables;
import io.openaev.ee.EnterpriseEditionService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Shared Spring context for the by-id request-scope suites that run with the unmodified production
 * list of isolated tables: {@link ByIdRequestScopeTest} and {@link ByIdRequestScopePrimaryKeyTest}
 * declare no configuration of their own, so they resolve to this one context instead of a context
 * each. {@link ByIdRequestScopeArmedTest} arms two extra tables and keeps its own.
 */
abstract class ByIdRequestScopeProductionTablesTestBase extends ByIdRequestScopeTestBase {

  // The autonomous launch is EE-gated; the mock's license checks default to "active".
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }
}
