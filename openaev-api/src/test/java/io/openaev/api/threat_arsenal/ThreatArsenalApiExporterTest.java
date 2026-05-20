package io.openaev.api.threat_arsenal;

import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Domain;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.User;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.ThreatArsenalInputFixture;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.helpers.UserTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class ThreatArsenalApiExporterTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DomainComposer domainComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private UserTestHelper userTestHelper;

  @BeforeEach
  void beforeEach() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant();
    injectorContractComposer.reset();
    domainComposer.reset();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("JSON Export Threat Arsenal Action")
  class ExportThreatArsenalAction {

    private static final String EXPORT_URL =
        ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL + "/{actionId}/export";

    @Test
    @DisplayName("Exporting a payload-based action should return a ZIP JSON:API document")
    void given_payloadBasedActionId_should_exportZipJsonApiDocument() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mockMvc
              .perform(
                  post(tenantUri(ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL))
                      .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                      .content(asJsonString(createInput))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String actionId = JsonPath.read(createResponse, "$.injector_contract_id");

      // Act
      byte[] zipBytes =
          mockMvc
              .perform(get(tenantUri(EXPORT_URL).replace("{actionId}", actionId)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      // Assert
      assertThat(zipBytes).isNotEmpty();

      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
        ZipEntry entry;
        JsonNode document = null;

        while ((entry = zis.getNextEntry()) != null) {
          if (entry.getName().endsWith(".json") && !"meta.json".equals(entry.getName())) {
            document = objectMapper.readTree(zis.readAllBytes());
            break;
          }
        }

        assertThat(document).isNotNull();
        assertThat(document.at("/data/type").asText()).isEqualTo("injectors_contracts");
      }
    }

    @Test
    @DisplayName("Exporting an unknown action should return not found")
    void given_unknownActionId_should_returnNotFound() throws Exception {
      // Arrange
      String unknownActionId = UUID.randomUUID().toString();

      // Act / Assert
      mockMvc
          .perform(get(tenantUri(EXPORT_URL).replace("{actionId}", unknownActionId)))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Exporting a non-payload injector contract should return not found")
    void given_nonPayloadInjectorContract_should_returnNotFound() throws Exception {
      // Arrange
      InjectorContract nonPayloadContract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownEmailInjector(false))
              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()).persist())
              .persist()
              .get();

      // Act / Assert
      mockMvc
          .perform(get(tenantUri(EXPORT_URL).replace("{actionId}", nonPayloadContract.getId())))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("CSV Export Threat Arsenal Actions")
  class ExportCsvThreatArsenalActions {

    private static final String EXPORT_CSV_URL =
        ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL + "/export/csv";

    private static Stream<Arguments> userAccessTestCases() {
      return Stream.of(
          Arguments.of(
              "User with no groups should be denied", UserTestHelper.UserType.NO_GROUPS, false),
          Arguments.of("Admin user should export CSV", UserTestHelper.UserType.ADMIN, true),
          Arguments.of(
              "User with BYPASS capability should export CSV",
              UserTestHelper.UserType.WITH_BYPASS,
              true),
          Arguments.of(
              "User with ACCESS_THREAT_ARSENALS capability should export CSV",
              UserTestHelper.UserType.WITH_ACCESS_THREAT_ARSENALS,
              true));
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("userAccessTestCases")
    @DisplayName("POST /export/csv - Test access control for different user types")
    void given_user_should_exportCsvBasedOnRole(
        String testCase, UserTestHelper.UserType userType, boolean shouldSucceed) throws Exception {
      // Arrange
      User testUser = userTestHelper.createTestUser(userType, List.of()).persist().get();
      Authentication auth = buildAuthenticationToken(testUser);

      String tenantId = TenantContext.getCurrentTenant();
      tenantRepository.addUserToTenant(testUser.getId(), tenantId);

      String exportUrl = EXPORT_CSV_URL.replace("{tenantId}", tenantId);
      SearchPaginationInput input = new SearchPaginationInput();

      // Act
      var result =
          mockMvc.perform(
              post(exportUrl)
                  .with(authentication(auth))
                  .with(csrf())
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)));

      // Assert
      if (shouldSucceed) {
        result.andExpect(status().is2xxSuccessful());
      } else {
        result.andExpect(status().isForbidden());
      }
    }
  }
}
