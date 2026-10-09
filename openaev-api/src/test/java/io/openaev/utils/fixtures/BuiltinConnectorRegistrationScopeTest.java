package io.openaev.utils.fixtures;

import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.injectors.email.EmailContract.EMAIL_GLOBAL;
import static io.openaev.injectors.manual.ManualContract.MANUAL_DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registering a built-in connector reads the tenant's preset domains to decide whether to create
 * them. A fixture that calls the integration factory straight from the test thread carries no
 * tenant scope, so once {@code domains} is an active table every existing row is invisible, the
 * upsert concludes the preset is absent and the insert collides with the row already there. These
 * tests arm {@code domains} so the fixtures are exercised under the shape the platform runs in.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=domains")
@WithMockUser(isAdmin = true)
@DisplayName("built-in connector registration from a fixture, with domains armed")
class BuiltinConnectorRegistrationScopeTest extends IntegrationTest {

  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private InjectorFixture injectorFixture;

  @Nested
  @DisplayName("the well-known injector contract fixtures")
  class WellKnownInjectorContracts {

    @Test
    @DisplayName("given domains is active, should return the single email contract")
    void given_domainsIsActive_should_returnTheSingleEmailContract() {
      // -- ACT --
      InjectorContract contract = injectorContractFixture.getWellKnownSingleEmailContract();

      // -- ASSERT --
      assertThat(contract.getId()).isEqualTo(EMAIL_DEFAULT);
    }

    @Test
    @DisplayName("given domains is active, should return the global email contract")
    void given_domainsIsActive_should_returnTheGlobalEmailContract() {
      // -- ACT --
      InjectorContract contract = injectorContractFixture.getWellKnownGlobalEmailContract();

      // -- ASSERT --
      assertThat(contract.getId()).isEqualTo(EMAIL_GLOBAL);
    }

    @Test
    @DisplayName("given domains is active, should return the single manual contract")
    void given_domainsIsActive_should_returnTheSingleManualContract() {
      // -- ACT --
      InjectorContract contract = injectorContractFixture.getWellKnownSingleManualContract();

      // -- ASSERT --
      assertThat(contract.getId()).isEqualTo(MANUAL_DEFAULT);
    }
  }

  @Nested
  @DisplayName("the caller's transaction")
  class TheCallerTransaction {

    @Test
    @DisplayName("given no scope on the caller's transaction, should leave none behind")
    void given_noScopeOnTheCallerTransaction_should_leaveNoneBehind() {
      // -- PREPARE --
      // Nothing scopes this test's transaction: it carries no TxCtx, so the aspect stays inert.
      assertThat(currentScope()).isEmpty();

      // -- ACT --
      injectorContractFixture.getWellKnownSingleEmailContract();

      // -- ASSERT --
      // A scope left behind here would be refused as a redefinition by every endpoint this
      // transaction goes on to call with a TxCtx of its own.
      assertThat(currentScope()).isEmpty();
    }

    private String currentScope() {
      return (String)
          entityManager
              .createNativeQuery(
                  "SELECT coalesce(current_setting('app.current_tenants', true), '')")
              .getSingleResult();
    }
  }

  @Nested
  @DisplayName("the well-known injector fixtures")
  class WellKnownInjectors {

    @Test
    @DisplayName("given domains is active, should return the email injector")
    void given_domainsIsActive_should_returnTheEmailInjector() {
      // -- ACT --
      Injector injector = injectorFixture.getWellKnownEmailInjector(false);

      // -- ASSERT --
      assertThat(injector.getType()).isNotBlank();
    }

    @Test
    @DisplayName("given domains is active, should return the implant injector")
    void given_domainsIsActive_should_returnTheImplantInjector() {
      // -- ACT --
      Injector injector = injectorFixture.getWellKnownOaevImplantInjector();

      // -- ASSERT --
      assertThat(injector.getType()).isNotBlank();
    }
  }
}
