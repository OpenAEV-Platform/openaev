package io.openaev.rest.exercise;

import io.openaev.ee.EnterpriseEditionService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Shared Spring context for the by-id request-scope suites that reach Enterprise Edition endpoints:
 * {@link ByIdRequestScopeTest} and {@link ByIdRequestScopePrimaryKeyTest} declare no configuration
 * of their own, so they share this one context rather than a context each.
 */
abstract class ByIdRequestScopeEnterpriseEditionTestBase extends ByIdRequestScopeTestBase {

  // The autonomous launch is EE-gated; the mock's license checks default to "active".
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
}
