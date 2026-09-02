package io.openaev.aop.audit_log;

import static io.openaev.api.credentials.CredentialApi.GCP_PRIVATE_KEY_PART;
import static io.openaev.api.credentials.CredentialApi.TENANT_CREDENTIALS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_CLIENT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_CLIENT_SECRET;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_REFRESH_TOKEN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_PRIVATE_KEY_JSON;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_PROJECT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_SCOPE;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpPrivateKeyJsonBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.credentials.form.CredentialInput;
import io.openaev.database.model.Tenant;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.service.LogService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CredentialInputFixture;
import io.openaev.utils.log.dispatcher.AuditLogTransportDispatcherUtils;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asserts that credential secrets never reach the audit transport.
 *
 * <p>The audited payload is the request DTO captured by {@code AccessControlAuditLogAspect}: it is
 * the only place where the GCP OAuth2 client secret and refresh token ever appear in clear text.
 * The uploaded key file never even gets there — {@code
 * AccessControlAuditLogAspect.isNonSerializableArgument} keeps {@code MultipartFile} arguments out
 * of the captured payload.
 */
@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(
    properties = {
      "openaev.audit-logs.transports=console",
      "openaev.enabled-dev-features=CREDENTIAL_ASSET"
    })
@DisplayName("Credential audit log redaction tests")
class CredentialAuditLogTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private EntityManager entityManager;

  private final List<String> committedTenantIds = new ArrayList<>();

  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoSpyBean private LogService logService;
  @MockitoSpyBean private AuditLogTransportDispatcherUtils auditLogTransportDispatcherUtils;

  @BeforeEach
  void setup() {
    reset(auditLogger, logService, auditLogTransportDispatcherUtils);
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
    doReturn(true).when(auditLogger).isAuditLoggingValid(any());
    doReturn(true).when(logService).isEnabled();
  }

  @Nested
  @DisplayName("Create Credential")
  class CreateCredential {

    @Test
    @DisplayName("given_gcpOAuth2Credential_should_notAuditClientSecretNorRefreshToken")
    void given_gcpOAuth2Credential_should_notAuditClientSecretNorRefreshToken() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-audit-gcp-oauth");
      CredentialInput input = CredentialInputFixture.gcpOAuth2Input("gcp-audit-oauth");

      // Act
      mvc.perform(multipartCreate(tenantCredentialsUri(tenant.getId()), input, null))
          .andExpect(status().is2xxSuccessful());

      // Assert
      String audited = capturedAuditEvent();
      assertThat(audited)
          .doesNotContain(GCP_OAUTH_CLIENT_SECRET)
          .doesNotContain(GCP_OAUTH_REFRESH_TOKEN);
      // The identifiers stay readable: an audit trail without them would be useless
      assertThat(audited)
          .contains(GCP_OAUTH_CLIENT_ID)
          .contains(GCP_SCOPE)
          .contains(GCP_PROJECT_ID);
    }

    @Test
    @DisplayName("given_gcpServiceAccountCredential_should_notAuditUploadedKeyFile")
    void given_gcpServiceAccountCredential_should_notAuditUploadedKeyFile() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-audit-gcp-sa");
      CredentialInput input = CredentialInputFixture.gcpServiceAccountInput("gcp-audit-sa");

      // Act
      mvc.perform(
              multipartCreate(
                  tenantCredentialsUri(tenant.getId()), input, gcpPrivateKeyJsonBytes()))
          .andExpect(status().is2xxSuccessful());

      // Assert: MultipartFile arguments are never captured as the audited payload
      String audited = capturedAuditEvent();
      assertThat(audited).doesNotContain(GCP_PRIVATE_KEY_JSON).doesNotContain("private_key");
    }
  }

  /**
   * The credential write runs outside the ambient transaction ({@code
   * CredentialService.createCredential} is {@code Propagation.NOT_SUPPORTED}), so the tenant must
   * already be committed or the secret insert trips the {@code secrets_tenant_fk} constraint.
   */
  private Tenant createCommittedTenantWithCurrentUser(String name) throws Exception {
    Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser(name);
    committedTenantIds.add(tenant.getId());
    entityManager.flush();
    entityManager.clear();
    TestTransaction.flagForCommit();
    TestTransaction.end();
    TestTransaction.start();
    return tenant;
  }

  @AfterEach
  void cleanupCommittedTenants() {
    if (committedTenantIds.isEmpty()) {
      return;
    }
    tenantIsolationTestHelper.deleteCommittedTenants(committedTenantIds.toArray(String[]::new));
    committedTenantIds.clear();
  }

  /** Serializes the audit event handed to the transports, which is the redacted one. */
  private String capturedAuditEvent() throws Exception {
    ArgumentCaptor<LogEvent> eventCaptor = ArgumentCaptor.forClass(LogEvent.class);
    verify(auditLogTransportDispatcherUtils, timeout(5000).atLeastOnce())
        .dispatch(eventCaptor.capture(), any());
    return objectMapper.writeValueAsString(eventCaptor.getAllValues());
  }

  private MockHttpServletRequestBuilder multipartCreate(
      String uri, CredentialInput input, byte[] gcpPrivateKeyJson) {
    MockMultipartHttpServletRequestBuilder builder = multipart(uri);
    builder.file(
        new MockMultipartFile(
            "input",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            asJsonString(input).getBytes(StandardCharsets.UTF_8)));
    if (gcpPrivateKeyJson != null) {
      builder.file(
          new MockMultipartFile(
              GCP_PRIVATE_KEY_PART,
              "key.json",
              MediaType.APPLICATION_JSON_VALUE,
              gcpPrivateKeyJson));
    }
    return builder.with(csrf());
  }

  private String tenantCredentialsUri(String tenantId) {
    return TENANT_CREDENTIALS_URI.replace("{tenantId}", tenantId);
  }
}
