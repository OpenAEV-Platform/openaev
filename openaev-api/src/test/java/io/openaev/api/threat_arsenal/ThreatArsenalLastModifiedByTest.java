package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.fixtures.ThreatArsenalInputFixture;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.helpers.UserTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Threat Arsenal action: last modified by")
class ThreatArsenalLastModifiedByTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private DomainComposer domainComposer;
  @Autowired private UserTestHelper userTestHelper;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  @BeforeEach
  void beforeEach() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    domainComposer.reset();
    // The write endpoints resolve the write tenant from the user's memberships.
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
  }

  private String createCommandAction() throws Exception {
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    ThreatArsenalActionCreateInput input =
        ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));
    return mvc.perform(
            post(tenantUri(TENANT_THREAT_ARSENAL_URL))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input)))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private User otherAdmin() {
    User other =
        userTestHelper.createTestUser(UserTestHelper.UserType.ADMIN, List.of()).persist().get();
    tenantRepository.addUserToTenant(other.getId(), Tenant.DEFAULT_TENANT_UUID);
    return other;
  }

  private Payload reload(String payloadId) {
    entityManager.flush();
    entityManager.clear();
    return payloadRepository.findById(payloadId).orElseThrow();
  }

  @Test
  @DisplayName("Creating an action records its creator as author and as last modifier")
  void given_create_should_stampAuthorAndLastModifiedBy() throws Exception {
    // -- ACT --
    String response = createCommandAction();

    // -- ASSERT --
    String actionId = JsonPath.read(response, "$.injector_contract_id");
    String payloadId = JsonPath.read(response, "$.action_payload.payload_id");
    Payload payload = reload(payloadId);
    String currentUserId = testUserHolder.get().getId();
    assertThat(payload.getAuthorUser().getId()).isEqualTo(currentUserId);
    assertThat(payload.getLastModifiedBy().getId()).isEqualTo(currentUserId);

    String detail =
        mvc.perform(get(tenantUri(TENANT_THREAT_ARSENAL_URL) + "/" + actionId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat((String) JsonPath.read(detail, "$.action_last_modified_by"))
        .isEqualTo(currentUserId);
    assertThat((String) JsonPath.read(detail, "$.action_last_modified_by_name"))
        .isEqualTo(testUserHolder.get().getNameOrEmail());
  }

  @Test
  @DisplayName("Updating an action records the editor as last modifier and keeps the author")
  void given_updateByAnotherUser_should_changeLastModifiedByOnly() throws Exception {
    // -- ARRANGE --
    String response = createCommandAction();
    String actionId = JsonPath.read(response, "$.injector_contract_id");
    String payloadId = JsonPath.read(response, "$.action_payload.payload_id");
    String creatorId = testUserHolder.get().getId();
    User editor = otherAdmin();
    Authentication editorAuth = buildAuthenticationToken(editor);

    ThreatArsenalActionUpdateInput updateInput =
        new ThreatArsenalActionUpdateInput(
            "Updated name",
            new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows},
            "Updated description",
            "powershell",
            "echo updated",
            Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
            new BaseInjectExpectation.EXPECTATION_TYPE[] {},
            Collections.emptyMap(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            List.of());

    // -- ACT --
    mvc.perform(
            put(tenantUri(TENANT_THREAT_ARSENAL_URL) + "/" + actionId)
                .with(authentication(editorAuth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput)))
        .andExpect(status().is2xxSuccessful());

    // -- ASSERT --
    Payload payload = reload(payloadId);
    assertThat(payload.getAuthorUser().getId()).isEqualTo(creatorId);
    assertThat(payload.getLastModifiedBy().getId()).isEqualTo(editor.getId());
  }

  @Test
  @DisplayName("Duplicating an action records the duplicating user, not the origin's modifier")
  void given_duplicateByAnotherUser_should_stampDuplicatingUser() throws Exception {
    // -- ARRANGE --
    String response = createCommandAction();
    String actionId = JsonPath.read(response, "$.injector_contract_id");
    String originPayloadId = JsonPath.read(response, "$.action_payload.payload_id");
    String creatorId = testUserHolder.get().getId();
    User duplicator = otherAdmin();

    // -- ACT --
    String duplicateResponse =
        mvc.perform(
                post(tenantUri(TENANT_THREAT_ARSENAL_URL) + "/" + actionId + "/duplicate")
                    .with(authentication(buildAuthenticationToken(duplicator)))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String duplicatePayloadId = JsonPath.read(duplicateResponse, "$.action_payload.payload_id");
    assertThat(reload(duplicatePayloadId).getLastModifiedBy().getId())
        .isEqualTo(duplicator.getId());
    assertThat(reload(originPayloadId).getLastModifiedBy().getId()).isEqualTo(creatorId);
  }

  @Test
  @DisplayName("A collector upsert is not attributed to a user")
  void given_upsert_should_clearLastModifiedBy() throws Exception {
    // -- ARRANGE --
    Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
    PayloadUpsertInput upsertInput =
        PayloadInputFixture.getDefaultCommandPayloadUpsertInput(Set.of(domain));
    upsertInput.setExternalId("last-modified-by-upsert");
    String created =
        mvc.perform(
                post(PAYLOAD_URI + "/upsert")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(upsertInput))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String payloadId = JsonPath.read(created, "$.payload_id");
    Payload payload = reload(payloadId);
    assertThat(payload.getLastModifiedBy()).isNull();

    // A user-attributed payload re-synced by its collector loses the user attribution.
    payload.setLastModifiedBy(testUserHolder.get());
    payloadRepository.save(payload);
    entityManager.flush();

    // -- ACT --
    mvc.perform(
            post(PAYLOAD_URI + "/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(upsertInput))
                .with(csrf()))
        .andExpect(status().isOk());

    // -- ASSERT --
    assertThat(reload(payloadId).getLastModifiedBy()).isNull();
  }

  @Test
  @DisplayName("Export never carries the last modifier, import records the importing user")
  void given_exportThenImportByAnotherUser_should_stampImporter() throws Exception {
    // -- ARRANGE --
    String response = createCommandAction();
    String actionId = JsonPath.read(response, "$.injector_contract_id");
    byte[] exportedZip =
        mvc.perform(get(tenantUri(TENANT_THREAT_ARSENAL_URL) + "/" + actionId + "/export"))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(exportedDocument(exportedZip)).doesNotContain("payload_last_modified_by");
    User importer = otherAdmin();

    // -- ACT --
    String importResponse =
        mvc.perform(
                multipart(tenantUri(TENANT_THREAT_ARSENAL_URL) + "/import")
                    .file(
                        new MockMultipartFile(
                            "file", "threat-arsenal.zip", "application/zip", exportedZip))
                    .with(authentication(buildAuthenticationToken(importer)))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String importedPayloadId = JsonPath.read(importResponse, "$.action_payload.payload_id");
    assertThat(reload(importedPayloadId).getLastModifiedBy().getId()).isEqualTo(importer.getId());
  }

  private static String exportedDocument(byte[] zipBytes) throws Exception {
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName().endsWith(".json") && !"meta.json".equals(entry.getName())) {
          return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
        }
      }
    }
    throw new AssertionError("No JSON:API document in the export");
  }
}
