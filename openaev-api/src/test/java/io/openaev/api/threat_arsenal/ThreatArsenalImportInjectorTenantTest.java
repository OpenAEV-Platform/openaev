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
import io.openaev.database.repository.InjectorRepository;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.jsonapi.IncludeOptions;
import io.openaev.jsonapi.ZipJsonApi;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Map;
import java.util.UUID;
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
  @Autowired private InjectorRepository injectorRepository;

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
    @DisplayName(
        "given_aForeignTenantHoldsAPayloadInjectorOfTheSameType_should_linkWriteTenantInjectorOnly")
    void given_aForeignTenantHoldsAPayloadInjectorOfTheSameType_should_linkWriteTenantInjectorOnly()
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
      // The well-known injector's id repeats across tenants (#8485), so
      // `InjectorContract#addInjector`
      // (which dedupes by id) retains only whichever tenant's copy the unordered lookup meets
      // first, making a plain scan-order trick flaky. A payload injector with its own distinct id
      // is never deduped against the write tenant's copy, so it reaches the unfiltered lookup
      // regardless of row order and always pollutes the result without the write-tenant filter in
      // place.
      createForeignPayloadInjector(Tenant.DEFAULT_TENANT_UUID);

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

  /** A payload-capable injector of a distinct id, held by {@code tenantId}. */
  private Injector createForeignPayloadInjector(String tenantId) {
    Injector injector =
        InjectorFixture.createInjector(
            UUID.randomUUID().toString(),
            "foreign-tenant-payload-injector",
            OpenAEVImplantContract.TYPE);
    injector.setTenantId(tenantId);
    injector.setPayloads(true);
    return injectorRepository.save(injector);
  }
}
