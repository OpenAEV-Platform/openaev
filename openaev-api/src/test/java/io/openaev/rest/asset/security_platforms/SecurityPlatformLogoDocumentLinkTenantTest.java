package io.openaev.rest.asset.security_platforms;

import static io.openaev.rest.asset.security_platforms.SecurityPlatformApi.SECURITY_PLATFORM_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.rest.asset.security_platforms.form.SecurityPlatformInput;
import io.openaev.utils.JsonTestUtils;
import io.openaev.utils.TagLinkJdbc;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A security platform's logos resolve the document id named in the body within the security
 * platform's own tenant: an id of another tenant is treated like an unknown one, the same as the
 * pre-existing behaviour on a wholly unknown id (the logo is set to {@code null}, not refused).
 *
 * <p>The security platform update endpoint carries no write-scope constraint of its own (unlike
 * create, which {@code TenantWriteScopeResolver} already pins to a single tenant), so its {@code
 * TxCtx} can legitimately span several tenants on the header route: that is what let the old
 * unscoped {@code documentRepository.findById} match a document of any tenant in that scope.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A security platform's logos resolve their documents within the platform's own tenant")
class SecurityPlatformLogoDocumentLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String LOGO_DARK =
      "SELECT security_platform_logo_dark FROM assets WHERE asset_id = ?";
  private static final String LOGO_LIGHT =
      "SELECT security_platform_logo_light FROM assets WHERE asset_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private EntityManager entityManager;

  private String otherTenant;
  private String platformId;
  private String documentInOther;
  private String documentInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    securityPlatformComposer.reset();
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("security-platform-logo-link-o").getId();

    SecurityPlatform platform =
        SecurityPlatformFixture.createDefault(
            "security-platform-logo-link-" + UUID.randomUUID(),
            SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM.toString());
    platform.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    platformId = securityPlatformComposer.forSecurityPlatform(platform).persist().get().getId();

    documentInOther = saveDocument(otherTenant);
    documentInDefault = saveDocument(Tenant.DEFAULT_TENANT_UUID);

    entityManager.flush();
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName(
      "given a security platform of the default tenant when its logos are updated with a"
          + " document of another tenant, named through the header route, then the logos are"
          + " left null")
  void given_bodyNamingTheOtherTenant_should_setNullLogosThroughHeader() throws Exception {
    // -- Act & Assert --
    mvc.perform(
            put(SECURITY_PLATFORM_URI + "/{securityPlatformId}", platformId)
                .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonTestUtils.asJsonString(inputWithLogos(documentInOther)))
                .with(csrf()))
        .andExpect(status().isOk());
    assertThat(logoColumn(LOGO_DARK)).containsExactly((String) null);
    assertThat(logoColumn(LOGO_LIGHT)).containsExactly((String) null);
  }

  @Test
  @DisplayName(
      "given a security platform of the default tenant when its logos are updated with its own"
          + " document, named through the header route, then the logos are set")
  void given_bodyNamingItsOwnDocument_should_setLogosThroughHeader() throws Exception {
    // -- Act & Assert --
    mvc.perform(
            put(SECURITY_PLATFORM_URI + "/{securityPlatformId}", platformId)
                .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonTestUtils.asJsonString(inputWithLogos(documentInDefault)))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.security_platform_logo_dark").value(documentInDefault))
        .andExpect(jsonPath("$.security_platform_logo_light").value(documentInDefault));
  }

  // -- Helpers --

  private List<String> logoColumn(String sql) {
    return TagLinkJdbc.column(entityManager, sql, platformId);
  }

  private static SecurityPlatformInput inputWithLogos(String documentId) {
    SecurityPlatformInput input = new SecurityPlatformInput();
    input.setName("security-platform-logo-link-updated");
    input.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);
    input.setLogoDark(documentId);
    input.setLogoLight(documentId);
    return input;
  }

  private String saveDocument(String tenantId) {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setName("security-platform-logo-link-" + UUID.randomUUID());
    document.setTenant(new Tenant(tenantId));
    return documentRepository.save(document).getId();
  }
}
