package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.jsonapi.IncludeOptions;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.util.Map;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Importing a threat arsenal action links the payload injectors of the write tenant")
class ThreatArsenalImportInjectorTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private ZipJsonApi<InjectorContract> injectorContractZipJsonApi;
  @Autowired private InjectorContractRepository injectorContractRepository;

  private String tenantB;

  @BeforeEach
  void seedWriteTenant() throws Exception {
    injectorContractComposer.reset();
    payloadComposer.reset();
    // The caller belongs to the default tenant, where the source action is composed, and to B, the
    // write tenant selected by the header.
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("arsenal-import-injectors-b").getId();
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Injector contract export imported under another tenant")
  class ContractExportImport {

    @Test
    @DisplayName("given_foreignTenantInjectorComesFirst_should_linkWriteTenantInjectorOnly")
    void given_foreignTenantInjectorComesFirst_should_linkWriteTenantInjectorOnly()
        throws Exception {
      // Arrange
      InjectorContract source =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
              .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();
      byte[] export =
          injectorContractZipJsonApi
              .handleExport(
                  injectorContractRepository.findById(source.getId()).orElseThrow(),
                  null,
                  IncludeOptions.of(
                      Map.of(
                          "exclude from action export",
                          IncludeOptions.IncludeMode.FALSE,
                          "exclude from payload export",
                          IncludeOptions.IncludeMode.FALSE)),
                  null)
              .getBody();
      entityManager.clear();
      rewriteInjectorsOfTenantLast(tenantB);

      // Act
      String response =
          mvc.perform(
                  multipart(THREAT_ARSENAL_URL + "/import")
                      .file(new MockMultipartFile("file", "action.zip", "application/zip", export))
                      .header(TENANT_HEADER, tenantB)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      InjectorContract imported =
          injectorContractRepository
              .findById(JsonPath.<String>read(response, "$.injector_contract_id"))
              .orElseThrow();
      assertThat(imported.getInjectors())
          .as("the imported contract links the payload injector of the write tenant")
          .isNotEmpty()
          .extracting(Injector::getTenantId)
          .containsOnly(tenantB);
    }
  }

  /**
   * Updating a row writes a new tuple version, which a sequential scan meets after the tuples
   * already on the page: the injectors of {@code tenantId} then come last in the lookup of the
   * payload-capable injectors, which is unordered and spans every tenant when the table is not
   * armed in this class.
   */
  private void rewriteInjectorsOfTenantLast(String tenantId) {
    entityManager.flush();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "UPDATE injectors SET injector_payloads = injector_payloads"
                          + " WHERE tenant_id::text = ?")) {
                statement.setString(1, tenantId);
                statement.executeUpdate();
              }
            });
    entityManager.clear();
  }
}
