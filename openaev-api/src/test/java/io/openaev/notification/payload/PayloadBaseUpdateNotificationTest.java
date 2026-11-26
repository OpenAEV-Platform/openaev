package io.openaev.notification.payload;

import static io.openaev.api.catalog.CatalogRoutes.TENANT_CATALOG_URI;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.database.model.NotificationTriggerEventType.BASE_UPDATE_AVAILABLE;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.PAYLOAD_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.manifest;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadVersion;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.releasedPayload;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.syncRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.notification.catalog.CatalogEvents.InstalledAutomatically;
import io.openaev.notification.content.ContentNotification;
import io.openaev.notification.content.ContentNotifier;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CatalogReleaseFixture.ReleasedPayload;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=CATALOG")
@DisplayName("A newer Filigran version tells the customized copies built on an older one")
class PayloadBaseUpdateNotificationTest extends IntegrationTest {

  private static final ReleasedPayload RELEASED = releasedPayload();
  private static final String RELEASE = "1.261123.0";
  private static final String FIRST = "1.0.0";
  private static final String NEWER = "1.1.0";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private PayloadService payloadService;
  @Autowired private PayloadVersionService payloadVersionService;

  private NotificationEngineService engine;
  private PayloadInstallNotificationListener listener;
  private String base;

  @BeforeEach
  void installTheFirstVersion() throws Exception {
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
    injectorFixture.getWellKnownOaevImplantInjector();
    mvc.perform(
            syncRequest(
                    mapper.writeValueAsBytes(
                        manifest(
                            RELEASE,
                            List.of(),
                            List.of(
                                payloadVersion(FIRST, "VERIFIED", RELEASED),
                                payloadVersion(NEWER, "VERIFIED", RELEASED)))),
                    Map.of(
                        payloadFileName(FIRST), RELEASED.file(),
                        payloadFileName(NEWER), RELEASED.file()),
                    TENANT_CATALOG_URI + "/sync",
                    DEFAULT_TENANT_UUID)
                .with(csrf()))
        .andExpect(status().isOk());
    base = install(FIRST);
    // The listener runs after commit with no transaction: here it reads in the test's own.
    engine = mock(NotificationEngineService.class);
    TenantScopedTransaction tenantTx = mock(TenantScopedTransaction.class);
    when(tenantTx.executeReadOnly(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
    listener =
        new PayloadInstallNotificationListener(
            new ContentNotifier(engine, tenantTx), payloadService, payloadVersionService);
  }

  // -- HELPERS --

  private String install(String version) throws Exception {
    JsonNode installed =
        body(
            mvc.perform(
                    post(
                            TENANT_CATALOG_URI
                                + "/payloads/{externalId}/versions/{version}/install",
                            DEFAULT_TENANT_UUID,
                            PAYLOAD_ID,
                            version)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    entityManager.flush();
    entityManager.clear();
    return installed.path("installed_id").asText();
  }

  private String customize(String payloadId) throws Exception {
    InjectorContract action =
        injectorContractRepository
            .findInjectorContractByPayload(payloadRepository.findById(payloadId).orElseThrow())
            .orElseThrow();
    JsonNode copy =
        body(
            mvc.perform(
                    post(
                            TENANT_THREAT_ARSENAL_URL + "/{actionId}/customize",
                            DEFAULT_TENANT_UUID,
                            action.getId())
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    entityManager.flush();
    entityManager.clear();
    return copy.path("action_payload").path("payload_id").asText();
  }

  private void releaseInstallsTheNewerVersion() throws Exception {
    String newer = install(NEWER);
    listener.onInstalledAutomatically(
        new InstalledAutomatically(
            DEFAULT_TENANT_UUID, RELEASE, List.of(newer), List.of(), List.of()));
  }

  private JsonNode body(String content) throws Exception {
    return mapper.readTree(content);
  }

  private void verifyNoCopyTold() {
    verify(engine, never())
        .handleEventWithMessage(
            any(), anyString(), anyString(), eq(BASE_UPDATE_AVAILABLE), anyString());
  }

  // -- TESTS --

  @Test
  @DisplayName("A release installing a newer version tells the copy built on the older one")
  void given_copyOfOlderVersion_should_tellTheCopy() throws Exception {
    // Arrange
    String copyId = customize(base);
    Payload copy = payloadRepository.findById(copyId).orElseThrow();

    // Act
    releaseInstallsTheNewerVersion();

    // Assert
    verify(engine)
        .handleEventWithMessage(
            NotificationResourceCatalog.PAYLOAD,
            copyId,
            DEFAULT_TENANT_UUID,
            BASE_UPDATE_AVAILABLE,
            "[payload] "
                + ContentNotification.named(copy)
                + ": Filigran v"
                + NEWER
                + " is available");
  }

  @Test
  @DisplayName("A newer version no copy is built on tells no copy")
  void given_noCopy_should_tellNoCopy() throws Exception {
    // Act
    releaseInstallsTheNewerVersion();

    // Assert
    verifyNoCopyTold();
  }

  @Test
  @DisplayName("A copy in another tenant is not told")
  void given_copyInOtherTenant_should_tellNoCopy() throws Exception {
    // Arrange
    String otherTenant = UUID.randomUUID().toString();
    String otherCopy = UUID.randomUUID().toString();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        otherTenant,
        "base-update-" + otherTenant);
    jdbc.update(
        "INSERT INTO payload_identities (payload_identity_id, tenant_id, payload_source)"
            + " VALUES (?, ?, 'MANUAL')",
        otherCopy,
        otherTenant);
    jdbc.update(
        "INSERT INTO payloads (payload_id, payload_type, payload_name, tenant_id, payload_identity,"
            + " payload_version, payload_base) VALUES (?, 'Command', 'other-copy', ?, ?, ?, ?)",
        otherCopy,
        otherTenant,
        otherCopy,
        FIRST,
        base);

    // Act
    releaseInstallsTheNewerVersion();

    // Assert
    verifyNoCopyTold();
  }
}
