package io.openaev.injectors.phishing.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Inject;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.database.repository.PhishingResultRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code phishing_results} is tenant-active, so {@code createResult} attributes the row's tenant
 * explicitly instead of relying on {@code TenantBaseListener}, and it takes that tenant from the
 * inject its caller already holds. {@code PhishingTrackingServiceIntegrationTest} exercises {@code
 * createResult} but never activates {@code phishing_results} and only ever runs in the default
 * tenant, so an attribution that fell back to the ambient {@link TenantContext} would still pass
 * there: the default-tenant fallback produces the same row either way. This class activates the
 * table, puts the inject in a tenant of its own and clears the ambient tenant entirely before
 * writing, then reads the row back by SQL - the one case the ambient tenant cannot get right.
 */
@SpringBootTest
@TestPropertySource(properties = "openaev.tenant.active-tables=phishing_results")
@WithMockUser(isAdmin = true)
class PhishingTrackingServiceWriteAttributionTest extends IntegrationTest {

  @Autowired private PhishingTrackingService phishingTrackingService;
  @Autowired private PhishingResultRepository phishingResultRepository;
  @Autowired private PhishingLandingPageRepository phishingLandingPageRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private InjectComposer injectComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Nested
  @DisplayName("createResult with the inject in a non-default tenant")
  class CreateResultWithInjectInNonDefaultTenant {

    private Tenant tenantB;
    private Inject inject;
    private PhishingLandingPage landingPage;
    private User user;
    private PhishingResult created;

    @AfterEach
    void tearDown() {
      // The fixtures live in tenant B and Inject is still filtered on the ambient tenant, so the
      // cleanup reads only find them under B.
      if (tenantB != null) {
        TenantContext.setCurrentTenant(tenantB.getId());
      }
      if (created != null) {
        phishingResultRepository.deleteById(created.getId());
      }
      if (landingPage != null) {
        phishingLandingPageRepository.deleteById(landingPage.getId());
      }
      if (inject != null && injectRepository.existsById(inject.getId())) {
        injectRepository.deleteById(inject.getId());
      }
      if (user != null && userRepository.existsById(user.getId())) {
        userRepository.deleteById(user.getId());
      }
      TenantContext.clearCurrentTenant();
    }

    @Test
    @DisplayName("should attribute the row to the inject's tenant with no ambient tenant set")
    void given_injectInTenantBAndNoAmbientTenant_should_writeTenantBOnTheRow() throws Exception {
      // Arrange
      tenantB = tenantHelper.createTenantWithCurrentUser("wattr-phishing-b");
      TenantContext.setCurrentTenant(tenantB.getId());
      inject = injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
      landingPage = new PhishingLandingPage();
      landingPage.setName("wattr phishing landing page");
      // Explicit, not left to PhishingLandingPage's own v1 TenantBaseListener (removed on the
      // phishing_landing_pages activation): the fixture row needs a tenant of its own.
      landingPage.setTenant(tenantB);
      landingPage = phishingLandingPageRepository.save(landingPage);
      user = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      assertThat(inject.getTenant().getId())
          .as("the fixture inject must be the one carrying tenant B")
          .isEqualTo(tenantB.getId());
      // Production's send loop runs with a tenant on the thread; clearing it here is what makes the
      // assertion below discriminating. A test fixture leaves one behind, and nothing clears it
      // before a call that is not an HTTP request.
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.hasCurrentTenant())
          .as("no ambient tenant must be set, so only the inject can attribute the row")
          .isFalse();

      // Act
      created =
          phishingTrackingService.createResult(
              inject, inject.getTenant().getId(), landingPage, user.getId(), null, null);

      // Assert: read the row's tenant back by SQL, independent of the ORM's own tenant filter.
      String writtenTenantId =
          jdbcTemplate.queryForObject(
              "SELECT tenant_id FROM phishing_results WHERE phishing_result_id = ?",
              String.class,
              created.getId());
      assertThat(writtenTenantId)
          .as("the row must carry the inject's tenant, not the platform default")
          .isEqualTo(tenantB.getId())
          .isNotEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }
  }
}
