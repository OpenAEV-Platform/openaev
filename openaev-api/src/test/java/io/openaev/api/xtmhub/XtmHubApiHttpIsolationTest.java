package io.openaev.api.xtmhub;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.xtmhub.XtmHubClient;
import io.openaev.xtmhub.XtmHubRegistrationStatus;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that activating {@code tenant_xtmhub_registrations} scopes the real XTM Hub HTTP
 * endpoints. The table is one-row-per-tenant, so the tenant path must expose only that tenant's
 * registration and write paths must attribute or refuse based on the request scope, never the v1
 * thread-local default tenant fallback.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=tenant_xtmhub_registrations")
@WithMockUser(isAdmin = true)
@DisplayName("tenant_xtmhub_registrations isolation through the real XTM Hub HTTP endpoints")
class XtmHubApiHttpIsolationTest extends IntegrationTest {

  private static final String REGISTRATION_URI = XtmHubApi.TENANT_XTMHUB_URI + "/registration";
  private static final String REGISTER_URI = XtmHubApi.TENANT_XTMHUB_URI + "/register";
  private static final String UNREGISTER_URI = XtmHubApi.TENANT_XTMHUB_URI + "/unregister";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @MockitoBean private XtmHubClient xtmHubClient;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("xtmhub-http-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("xtmhub-http-b").getId();
    seedRegistration(tenantA, "token-a");
    seedRegistration(tenantB, "token-b");
  }

  @Test
  @DisplayName("given tenant A path when reading registration then tenant A data is returned")
  void givenTenantAPath_whenReadingRegistration_thenTenantADataIsReturned() throws Exception {
    // Arrange

    // Act
    String response =
        mvc.perform(get(REGISTRATION_URI, tenantA).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertEquals(
        "token-a",
        JsonPath.read(response, "$.tenant_xtmhub_registration_token"),
        "tenant A must read only its own registration");
  }

  @Test
  @DisplayName(
      "given only tenant B has a registration when reading under tenant A path then no content is returned")
  void givenOnlyTenantBRegistration_whenReadingUnderTenantAPath_thenNoContent() throws Exception {
    // Arrange
    deleteByTenantId(tenantA);

    // Act
    mvc.perform(get(REGISTRATION_URI, tenantA).accept(MediaType.APPLICATION_JSON))
        // Assert
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("given tenant C path when registering then the row is attributed to tenant C")
  void givenTenantCPath_whenRegistering_thenRowIsAttributedToTenantC() throws Exception {
    // Arrange
    String tenantC = tenantHelper.createTenantWithCurrentUser("xtmhub-http-c").getId();
    XtmHubRegisterInput input = XtmHubRegisterInput.builder().token("token-c").build();

    // Act
    String response =
        mvc.perform(
                put(REGISTER_URI, tenantC)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertEquals(
        tenantC,
        rawTenantId(JsonPath.read(response, "$.tenant_xtmhub_registration_id")),
        "the created row must belong to the tenant named by the path");
  }

  @Test
  @DisplayName(
      "given a multi-tenant caller with no selector when registering then the write is refused with 400")
  void givenMultiTenantCallerWithoutSelector_whenRegistering_thenBadRequest() throws Exception {
    // Arrange
    XtmHubRegisterInput input = XtmHubRegisterInput.builder().token("no-selector").build();

    // Act / Assert
    mvc.perform(
            put(XtmHubApi.XTMHUB_URI + "/register")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "given tenant A header selector when auto-registering then the row is attributed to tenant A")
  void givenHeaderSelector_whenAutoRegistering_thenRowIsAttributedToHeaderTenant()
      throws Exception {
    // Arrange
    String tenantC = tenantHelper.createTenantWithCurrentUser("xtmhub-http-auto-c").getId();
    when(xtmHubClient.autoRegister(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(true);
    XtmHubRegisterInput input = XtmHubRegisterInput.builder().token("auto-token-c").build();

    // Act
    mvc.perform(
            put(XtmHubApi.XTMHUB_URI + "/auto-register")
                .header("X-Tenant-Ids", tenantC)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());

    // Assert
    assertEquals(
        1L,
        rawCountForTokenInTenant("auto-token-c", tenantC),
        "auto-register must attribute the row to the header-selected tenant");
  }

  @Test
  @DisplayName(
      "given tenant A already has a registration when registering again then the same row is reused")
  void givenExistingTenantARegistration_whenRegisteringAgain_thenSameRowIsReused()
      throws Exception {
    // Arrange
    String existingRegistrationId = registrationIdForTenant(tenantA);
    XtmHubRegisterInput input = XtmHubRegisterInput.builder().token("token-a-updated").build();

    // Act
    String response =
        mvc.perform(
                put(REGISTER_URI, tenantA)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertEquals(
        existingRegistrationId,
        JsonPath.read(response, "$.tenant_xtmhub_registration_id"),
        "register must reuse tenant A's single row");
    assertEquals(1L, rawCountForTenant(tenantA), "tenant A must still own exactly one row");
    assertEquals("token-a-updated", rawTokenForTenant(tenantA), "tenant A's row must be updated");
  }

  @Test
  @DisplayName(
      "given tenant A path when unregistering then only tenant A row is deleted and tenant B survives")
  void givenTenantAPath_whenUnregistering_thenOnlyTenantARowIsDeleted() throws Exception {
    // Arrange

    // Act
    mvc.perform(put(UNREGISTER_URI, tenantA).contentType(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isNoContent());

    // Assert
    assertEquals(0L, rawCountForTenant(tenantA), "tenant A's row must be deleted");
    assertEquals(1L, rawCountForTenant(tenantB), "tenant B's row must stay untouched");
    assertEquals("token-b", rawTokenForTenant(tenantB), "tenant B must keep its registration");
  }

  private void seedRegistration(String tenantId, String token) {
    TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
    registration.setId(UUID.randomUUID().toString());
    registration.setToken(token);
    registration.setRegistrationDate(LocalDateTime.now());
    registration.setRegistrationStatus(XtmHubRegistrationStatus.REGISTERED);
    registration.setRegistrationUserId("user-" + tenantId);
    registration.setRegistrationUserName("User " + tenantId);
    registration.setLastConnectivityCheck(LocalDateTime.now());
    registration.setConnectivityEmailEligible(true);
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      """
                      INSERT INTO tenant_xtmhub_registrations (
                        registration_id,
                        tenant_id,
                        registration_token,
                        registration_date,
                        registration_status,
                        registration_user_id,
                        registration_user_name,
                        registration_last_connectivity_check,
                        registration_connectivity_email_eligible
                      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                      """)) {
                statement.setString(1, registration.getId());
                statement.setString(2, tenantId);
                statement.setString(3, token);
                statement.setObject(4, registration.getRegistrationDate());
                statement.setString(5, registration.getRegistrationStatus().name());
                statement.setString(6, registration.getRegistrationUserId());
                statement.setString(7, registration.getRegistrationUserName());
                statement.setObject(8, registration.getLastConnectivityCheck());
                statement.setBoolean(9, registration.isConnectivityEmailEligible());
                statement.executeUpdate();
              }
            });
  }

  private void deleteByTenantId(String tenantId) {
    entityManager.flush();
    jdbcTemplate.update("DELETE FROM tenant_xtmhub_registrations WHERE tenant_id = ?", tenantId);
  }

  private String rawTenantId(String registrationId) {
    entityManager.flush();
    return jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM tenant_xtmhub_registrations WHERE registration_id = ?",
        String.class,
        registrationId);
  }

  private long rawCountForTokenInTenant(String token, String tenantId) {
    entityManager.flush();
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM tenant_xtmhub_registrations"
                + " WHERE registration_token = ? AND tenant_id = ?",
            Long.class,
            token,
            tenantId);
    return count != null ? count : 0L;
  }

  private String registrationIdForTenant(String tenantId) {
    entityManager.flush();
    return jdbcTemplate.queryForObject(
        "SELECT registration_id FROM tenant_xtmhub_registrations WHERE tenant_id = ?",
        String.class,
        tenantId);
  }

  private long rawCountForTenant(String tenantId) {
    entityManager.flush();
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM tenant_xtmhub_registrations WHERE tenant_id = ?",
            Long.class,
            tenantId);
    return count != null ? count : 0L;
  }

  private String rawTokenForTenant(String tenantId) {
    entityManager.flush();
    return jdbcTemplate.queryForObject(
        "SELECT registration_token FROM tenant_xtmhub_registrations WHERE tenant_id = ?",
        String.class,
        tenantId);
  }
}
