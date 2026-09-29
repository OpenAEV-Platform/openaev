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
 * explicitly from the ambient {@link TenantContext} (see its class javadoc) instead of relying on
 * {@code TenantBaseListener}. {@code PhishingTrackingServiceIntegrationTest} exercises {@code
 * createResult} but never activates {@code phishing_results} and never sets a non-default ambient
 * tenant, so a regression that dropped the explicit {@code setTenant} call would still pass there:
 * {@code TenantContext}'s default-tenant fallback produces the same row either way. This class
 * activates the table and runs under a genuinely different tenant, then reads the written row back
 * by SQL, so a real misattribution cannot hide behind the default-tenant coincidence.
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
  @DisplayName("createResult under a non-default tenant scope")
  class CreateResultUnderNonDefaultTenant {

    private Tenant tenantB;
    private Inject inject;
    private PhishingLandingPage landingPage;
    private User user;
    private PhishingResult created;

    @AfterEach
    void tearDown() {
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
    @DisplayName("should attribute the row to tenant B, not the default tenant")
    void given_ambientTenantB_should_writeTenantBOnTheRow() throws Exception {
      // Arrange
      tenantB = tenantHelper.createTenantWithCurrentUser("wattr-phishing-b");
      inject = injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
      landingPage = new PhishingLandingPage();
      landingPage.setName("wattr phishing landing page");
      landingPage = phishingLandingPageRepository.save(landingPage);
      user = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      TenantContext.setCurrentTenant(tenantB.getId());

      // Act
      created = phishingTrackingService.createResult(inject, landingPage, user.getId(), null, null);

      // Assert: read the row's tenant back by SQL, independent of the ORM's own tenant filter.
      String writtenTenantId =
          jdbcTemplate.queryForObject(
              "SELECT tenant_id FROM phishing_results WHERE phishing_result_id = ?",
              String.class,
              created.getId());
      assertThat(writtenTenantId)
          .as("the row must carry the ambient tenant, not the platform default")
          .isEqualTo(tenantB.getId())
          .isNotEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }
  }
}
