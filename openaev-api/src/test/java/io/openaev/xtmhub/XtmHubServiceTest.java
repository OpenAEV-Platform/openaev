package io.openaev.xtmhub;

import static org.apache.hc.core5.http.HttpHeaders.ACCEPT;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.ee.License;
import io.openaev.ee.LicenseTypeEnum;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.UserService;
import io.openaev.utilstest.DefaultTenantExtension;
import io.openaev.xtmhub.config.XtmHubConfig;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.socket.PortFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith({MockitoExtension.class, DefaultTenantExtension.class})
class XtmHubServiceTest {

  private static final String GRAPHQL_PATH = "/graphql-api";
  private static final String HEADER_XTM_PLATFORM_TOKEN = "XTM-Hub-Platform-Token";
  private static final String HEADER_XTM_PLATFORM_ID = "XTM-Hub-Platform-Id";

  private static ClientAndServer mockServer;

  @Mock private PlatformSettingsService platformSettingsService;
  @Mock private UserService userService;
  @Mock private XtmHubEmailService xtmHubEmailService;
  @Mock private HttpClientFactory httpClientFactory;
  @Mock private TenantXtmHubRegistrationRepository tenantXtmHubRegistrationRepository;

  private XtmHubConfig xtmHubConfig;
  private XtmHubService xtmHubService;

  private PlatformSettings mockSettings;
  private LocalDateTime now;
  private LocalDateTime registrationDate;

  @BeforeAll
  static void startMockServer() {
    mockServer = ClientAndServer.startClientAndServer(PortFactory.findFreePort());
  }

  @AfterAll
  static void stopMockServer() {
    mockServer.stop();
  }

  @BeforeEach
  void setUp() {
    mockSettings = new PlatformSettings();
    now = LocalDateTime.now();
    registrationDate = now.minusDays(5);

    xtmHubConfig = new XtmHubConfig();
    xtmHubConfig.setUrl("http://localhost:" + mockServer.getLocalPort());
    xtmHubConfig.setConnectivityEmailEnable(true);

    // lenient: some tests (blank/null token) never reach the HTTP call
    lenient().when(httpClientFactory.httpClientCustom()).thenReturn(HttpClients.createDefault());
    // Default: no registration found
    lenient()
        .when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
        .thenReturn(Optional.empty());

    XtmHubClient xtmHubClient =
        new XtmHubClient(xtmHubConfig, httpClientFactory, platformSettingsService);
    xtmHubClient.init();

    xtmHubService =
        new XtmHubService(
            platformSettingsService,
            userService,
            xtmHubConfig,
            xtmHubClient,
            xtmHubEmailService,
            tenantXtmHubRegistrationRepository);
  }

  @AfterEach
  void resetMockServer() {
    mockServer.reset();
  }

  // =====================================================================
  // MockServer helpers
  // =====================================================================

  /** Stubs MockServer to return the given connectivity status label. */
  private void whenHubReturnsConnectivityStatus(String status) {
    mockServer
        .when(request().withMethod("POST").withPath(GRAPHQL_PATH))
        .respond(
            response()
                .withStatusCode(200)
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withBody(
                    "{\"data\":{\"refreshPlatformRegistrationConnectivityStatusSingleTenant\":{\"status\":\"%s\"}}}"
                        .formatted(status)));
  }

  /** Stubs MockServer to return the given autoRegister success flag. */
  private void whenHubAutoRegisters(boolean success) {
    mockServer
        .when(request().withMethod("POST").withPath(GRAPHQL_PATH))
        .respond(
            response()
                .withStatusCode(200)
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withBody(
                    "{\"data\":{\"autoRegisterPlatform\":{\"success\":%b}}}".formatted(success)));
  }

  /** Common GraphQL matcher with headers that all XtmHubClient POST calls should carry. */
  private HttpRequest graphqlPostRequestMatcher() {
    return request()
        .withMethod("POST")
        .withPath(GRAPHQL_PATH)
        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
        .withHeader(ACCEPT, APPLICATION_JSON_VALUE);
  }

  private HttpRequest graphqlPostRequestMatcherWithPlatformAuth(String token, String platformId) {
    return graphqlPostRequestMatcher()
        .withHeader(HEADER_XTM_PLATFORM_TOKEN, token)
        .withHeader(HEADER_XTM_PLATFORM_ID, platformId);
  }

  /** Verifies a single POST to the GraphQL endpoint and returns its parsed JSON body. */
  private JsonObject verifySingleGraphqlPostRequestAndGetBody(HttpRequest requestMatcher) {
    mockServer.verify(requestMatcher);

    var recorded =
        mockServer.retrieveRecordedRequests(request().withMethod("POST").withPath(GRAPHQL_PATH));
    assertThat(recorded).hasSize(1);

    return JsonParser.parseString(recorded[0].getBodyAsString()).getAsJsonObject();
  }

  /**
   * Verifies that a POST to /graphql-api was made with the expected authentication headers, then
   * returns the parsed {@code variables.input} JSON object so each caller can assert only the
   * fields it cares about — avoiding fragile full-body matching.
   */
  private JsonObject verifyAutoRegisterRequest(String token, String platformId) {
    JsonObject body =
        verifySingleGraphqlPostRequestAndGetBody(
            graphqlPostRequestMatcherWithPlatformAuth(token, platformId));
    assertThat(body.get("query").getAsString()).contains("autoRegisterPlatform");
    return body.getAsJsonObject("variables").getAsJsonObject("input");
  }

  /** Verifies refresh-connectivity GraphQL request headers and body. */
  private void verifyRefreshConnectivityRequest(
      String platformId, String platformVersion, String token, String platformBaseUrl) {
    JsonObject body = verifySingleGraphqlPostRequestAndGetBody(graphqlPostRequestMatcher());
    assertThat(body.get("query").getAsString())
        .contains("refreshPlatformRegistrationConnectivityStatus");

    JsonObject input = body.getAsJsonObject("variables").getAsJsonObject("input");
    assertThat(input.get("platformId").getAsString()).isEqualTo(platformId);
    assertThat(input.get("platformVersion").getAsString()).isEqualTo(platformVersion);
    assertThat(input.get("token").getAsString()).isEqualTo(token);
    assertThat(input.get("platformIdentifier").getAsString()).isEqualTo("openaev");
    assertThat(input.get("url").getAsString())
        .isEqualTo(platformBaseUrl + "/" + Tenant.DEFAULT_TENANT_UUID);
  }

  /** Asserts that no HTTP request was made to the hub at all. */
  private void verifyNoRequestSentToHub() {
    assertThat(mockServer.retrieveRecordedRequests(request())).isEmpty();
  }

  /** Builds a TenantXtmHubRegistration with the given token and lastConnectivityCheck. */
  private TenantXtmHubRegistration buildRegistration(String token, LocalDateTime lastCheck) {
    TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
    registration.setToken(token);
    registration.setRegistrationDate(registrationDate);
    registration.setRegistrationUserId("user-123");
    registration.setRegistrationUserName("John Doe");
    registration.setLastConnectivityCheck(lastCheck);
    return registration;
  }

  // =====================================================================
  // refreshConnectivity tests
  // =====================================================================

  @Test
  @DisplayName("Should call XTM Hub refresh endpoint when registration is present")
  void refreshConnectivity_WhenRegistrationIsPresent_ShouldCallXtmHub() {
    // Given
    String token = "valid-token";
    String platformId = "platform-123";
    String platformVersion = "1.0.0";
    String platformBaseUrl = "http://localhost";
    LocalDateTime lastCheck = now.minusHours(1);

    TenantXtmHubRegistration registration = buildRegistration(token, lastCheck);
    when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
        .thenReturn(Optional.of(registration));

    mockSettings.setPlatformId(platformId);
    mockSettings.setPlatformVersion(platformVersion);
    mockSettings.setPlatformBaseUrl(platformBaseUrl);
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");
    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    whenHubReturnsConnectivityStatus("active");

    // When
    xtmHubService.refreshConnectivity();

    // Then
    verifyRefreshConnectivityRequest(platformId, platformVersion, token, platformBaseUrl);
  }

  @Test
  @DisplayName("Should return null when no registration exists")
  void refreshConnectivity_WhenRegistrationIsAbsent_ShouldReturnNull() {
    // Given — repository returns empty by default (setUp)

    // When
    TenantXtmHubRegistration result = xtmHubService.refreshConnectivity();

    // Then
    assertNull(result);
    verifyNoRequestSentToHub();
    verifyNoInteractions(xtmHubEmailService);
    verify(platformSettingsService, never()).updateXTMHubEmailNotification(anyBoolean());
  }

  @Test
  @DisplayName("Should remove XTM Hub registration when platform is not found in the hub")
  void refreshConnectivity_WhenPlatformIsNotFound_ShouldRemoveRegistration() {
    // Given
    String token = "valid-token";
    String platformId = "platform-123";
    String platformVersion = "1.0.0";

    TenantXtmHubRegistration registration = buildRegistration(token, now.minusHours(1));
    when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
        .thenReturn(Optional.of(registration));

    mockSettings.setPlatformId(platformId);
    mockSettings.setPlatformVersion(platformVersion);
    mockSettings.setPlatformBaseUrl("http://localhost");

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    whenHubReturnsConnectivityStatus("not_found");

    // When
    xtmHubService.refreshConnectivity();

    // Then
    verify(platformSettingsService).deleteXTMHubRegistration();
    verifyNoInteractions(xtmHubEmailService);
  }

  @Test
  @DisplayName("Should update registration as REGISTERED when connectivity is ACTIVE")
  void refreshConnectivity_WhenConnectivityIsActive_ShouldUpdateAsRegistered() {
    // Given
    String token = "valid-token";
    LocalDateTime lastCheck = now.minusHours(12);

    TenantXtmHubRegistration registration = buildRegistration(token, lastCheck);
    when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
        .thenReturn(Optional.of(registration));

    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setPlatformBaseUrl("http://localhost");
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    whenHubReturnsConnectivityStatus("active");
    when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // When
    TenantXtmHubRegistration result = xtmHubService.refreshConnectivity();

    // Then
    ArgumentCaptor<TenantXtmHubRegistration> captor =
        ArgumentCaptor.forClass(TenantXtmHubRegistration.class);
    verify(tenantXtmHubRegistrationRepository).save(captor.capture());
    assertEquals(captor.getValue(), result);
    assertThat(captor.getValue().getRegistrationStatus())
        .isEqualTo(XtmHubRegistrationStatus.REGISTERED);

    verify(platformSettingsService).updateXTMHubEmailNotification(true);
    verifyNoInteractions(xtmHubEmailService);
  }

  @Test
  @DisplayName(
      "Should update registration as LOST_CONNECTIVITY when connectivity is not ACTIVE and not send email if less than 24 hours")
  void refreshConnectivity_WhenConnectivityLostLessThan24Hours_ShouldNotSendEmail() {
    // Given
    String token = "valid-token";
    LocalDateTime lastCheck = now.minusHours(12);

    TenantXtmHubRegistration registration = buildRegistration(token, lastCheck);
    when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
        .thenReturn(Optional.of(registration));

    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setPlatformBaseUrl("http://localhost");
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    whenHubReturnsConnectivityStatus("inactive");
    when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // When
    TenantXtmHubRegistration result = xtmHubService.refreshConnectivity();

    // Then
    ArgumentCaptor<TenantXtmHubRegistration> captor =
        ArgumentCaptor.forClass(TenantXtmHubRegistration.class);
    verify(tenantXtmHubRegistrationRepository).save(captor.capture());
    assertEquals(captor.getValue(), result);
    assertThat(captor.getValue().getRegistrationStatus())
        .isEqualTo(XtmHubRegistrationStatus.LOST_CONNECTIVITY);
    assertThat(captor.getValue().getLastConnectivityCheck()).isEqualTo(lastCheck);

    verify(platformSettingsService).updateXTMHubEmailNotification(true);
    verifyNoInteractions(xtmHubEmailService);
  }

  @Test
  @DisplayName("Should not send connectivity email when email is disabled from configuration")
  void refreshConnectivity_WhenEmailDisabledFromConfig_ShouldNotSendEmail() {
    // Given
    xtmHubConfig.setConnectivityEmailEnable(false);
    LocalDateTime lastCheck = now.minusHours(25);

    TenantXtmHubRegistration registration = buildRegistration("valid-token", lastCheck);
    when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
        .thenReturn(Optional.of(registration));

    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setPlatformBaseUrl("http://localhost");
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    whenHubReturnsConnectivityStatus("inactive");
    when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // When
    TenantXtmHubRegistration result = xtmHubService.refreshConnectivity();

    // Then
    assertNotNull(result);
    verifyNoInteractions(xtmHubEmailService);
  }

  @Test
  @DisplayName(
      "Should send connectivity email when connectivity is lost for more than 24 hours and email sending is enabled")
  void refreshConnectivity_WhenConnectivityLostMoreThan24HoursAndEmailEnabled_ShouldSendEmail() {
    // Given
    LocalDateTime lastCheck = now.minusHours(25);

    TenantXtmHubRegistration registration = buildRegistration("valid-token", lastCheck);
    when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
        .thenReturn(Optional.of(registration));

    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setPlatformBaseUrl("http://localhost");
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    whenHubReturnsConnectivityStatus("inactive");
    when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // When
    TenantXtmHubRegistration result = xtmHubService.refreshConnectivity();

    // Then
    assertNotNull(result);
    verify(xtmHubEmailService).sendLostConnectivityEmail();

    ArgumentCaptor<TenantXtmHubRegistration> captor =
        ArgumentCaptor.forClass(TenantXtmHubRegistration.class);
    verify(tenantXtmHubRegistrationRepository).save(captor.capture());
    assertThat(captor.getValue().getRegistrationStatus())
        .isEqualTo(XtmHubRegistrationStatus.LOST_CONNECTIVITY);

    verify(platformSettingsService).updateXTMHubEmailNotification(false);
  }

  @Test
  @DisplayName("Should not send email when connectivity is lost but email sending is disabled")
  void refreshConnectivity_WhenConnectivityLostButEmailDisabled_ShouldNotSendEmail() {
    // Given
    LocalDateTime lastCheck = now.minusHours(25);

    TenantXtmHubRegistration registration = buildRegistration("valid-token", lastCheck);
    when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
        .thenReturn(Optional.of(registration));

    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setPlatformBaseUrl("http://localhost");
    mockSettings.setXtmHubShouldSendConnectivityEmail("false");

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    whenHubReturnsConnectivityStatus("inactive");
    when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // When
    TenantXtmHubRegistration result = xtmHubService.refreshConnectivity();

    // Then
    assertNotNull(result);
    verifyNoInteractions(xtmHubEmailService);
    verify(platformSettingsService).updateXTMHubEmailNotification(true);
  }

  @Test
  @DisplayName("Should handle null lastConnectivityCheck by using current time")
  void refreshConnectivity_WhenLastConnectivityCheckIsNull_ShouldUseCurrentTime() {
    // Given
    TenantXtmHubRegistration registration = buildRegistration("valid-token", null);
    when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
        .thenReturn(Optional.of(registration));

    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setPlatformBaseUrl("http://localhost");
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    whenHubReturnsConnectivityStatus("inactive");
    when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // When
    TenantXtmHubRegistration result = xtmHubService.refreshConnectivity();

    // Then
    assertNotNull(result);
    verifyNoInteractions(xtmHubEmailService); // Not sent as it's considered first check
    verify(platformSettingsService).updateXTMHubEmailNotification(true);
  }

  @Test
  @DisplayName("Should handle exactly 24 hours difference")
  void refreshConnectivity_WhenExactly24HoursPassed_ShouldSendEmail() {
    // Given
    LocalDateTime lastCheck = now.minusHours(24);

    TenantXtmHubRegistration registration = buildRegistration("valid-token", lastCheck);
    when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
        .thenReturn(Optional.of(registration));

    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setPlatformBaseUrl("http://localhost");
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    whenHubReturnsConnectivityStatus("inactive");
    when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // When
    TenantXtmHubRegistration result = xtmHubService.refreshConnectivity();

    // Then
    assertNotNull(result);
    verify(xtmHubEmailService).sendLostConnectivityEmail();
    verify(platformSettingsService).updateXTMHubEmailNotification(false);
  }

  // =====================================================================
  // autoRegister tests
  // =====================================================================

  @Test
  @DisplayName("Should compute contract level as CE for non-enterprise license")
  void autoRegister_WithNonEnterpriseLicense_ShouldUseCEContract() {
    // Given
    String token = "valid-token";
    License license = new License();
    license.setLicenseEnterprise(false);
    mockSettings.setPlatformLicense(license);
    mockSettings.setPlatformId("platform-123");
    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(userService.globalCount()).thenReturn(1L);
    whenHubAutoRegisters(true);

    // When
    xtmHubService.autoRegister(token);

    // Then
    JsonObject input = verifyAutoRegisterRequest(token, "platform-123");
    assertThat(input.getAsJsonObject("platform").get("contract").getAsString()).isEqualTo("CE");
  }

  @Test
  @DisplayName("Should compute contract level as trial for enterprise trial license")
  void autoRegister_WithEnterpriseTrialLicense_ShouldUseTrialContract() {
    // Given
    String token = "valid-token";
    License license = new License();
    license.setLicenseEnterprise(true);
    license.setType(LicenseTypeEnum.trial);
    mockSettings.setPlatformLicense(license);
    mockSettings.setPlatformId("platform-123");
    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(userService.globalCount()).thenReturn(1L);
    whenHubAutoRegisters(true);

    // When
    xtmHubService.autoRegister(token);

    // Then
    JsonObject input = verifyAutoRegisterRequest(token, "platform-123");
    assertThat(input.getAsJsonObject("platform").get("contract").getAsString()).isEqualTo("trial");
  }

  @Test
  @DisplayName("Should compute contract level as EE for enterprise license")
  void autoRegister_WithEnterpriseStandardLicense_ShouldUseEEContract() {
    // Given
    String token = "valid-token";
    License license = new License();
    license.setLicenseEnterprise(true);
    license.setType(LicenseTypeEnum.standard);
    mockSettings.setPlatformLicense(license);
    mockSettings.setPlatformId("platform-123");
    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(userService.globalCount()).thenReturn(1L);
    whenHubAutoRegisters(true);

    // When
    xtmHubService.autoRegister(token);

    // Then
    JsonObject input = verifyAutoRegisterRequest(token, "platform-123");
    assertThat(input.getAsJsonObject("platform").get("contract").getAsString()).isEqualTo("EE");
  }

  @Test
  @DisplayName("Should update registration entity when auto-register succeeds")
  void autoRegister_WhenSuccessful_ShouldUpdateRegistrationStatus() {
    // Given
    String token = "valid-token";
    License license = new License();
    license.setLicenseEnterprise(true);
    license.setType(LicenseTypeEnum.trial);
    mockSettings.setPlatformLicense(license);
    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformName("Test Platform");
    mockSettings.setPlatformBaseUrl("http://localhost");
    mockSettings.setPlatformVersion("1.0.0");
    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(userService.globalCount()).thenReturn(1L);
    whenHubAutoRegisters(true);

    // When
    xtmHubService.autoRegister(token);

    // Then
    verify(platformSettingsService)
        .updateXTMHubRegistration(
            eq(token),
            any(LocalDateTime.class),
            eq(XtmHubRegistrationStatus.REGISTERED),
            isNull(),
            isNull(),
            eq(false));
  }

  @Test
  @DisplayName("Should send correct platform payload to XTM Hub")
  void autoRegister_WhenSuccessful_ShouldSendCorrectPayloadToHub() {
    // Given
    String token = "valid-token";
    License license = new License();
    license.setLicenseEnterprise(true);
    license.setType(LicenseTypeEnum.trial);
    mockSettings.setPlatformLicense(license);
    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformName("Test Platform");
    mockSettings.setPlatformBaseUrl("http://localhost");
    mockSettings.setPlatformVersion("1.0.0");
    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(userService.globalCount()).thenReturn(1L);
    whenHubAutoRegisters(true);

    // When
    xtmHubService.autoRegister(token);

    // Then
    JsonObject input = verifyAutoRegisterRequest(token, "platform-123");
    JsonObject platform = input.getAsJsonObject("platform");
    assertThat(platform.get("contract").getAsString()).isEqualTo("trial");
    assertThat(platform.get("id").getAsString()).isEqualTo("platform-123");
    assertThat(platform.get("title").getAsString()).isEqualTo("Test Platform");
    assertThat(platform.get("url").getAsString()).isEqualTo("http://localhost");
    assertThat(platform.get("version").getAsString()).isEqualTo("1.0.0");
    assertThat(input.get("existing_users_count").getAsLong()).isEqualTo(1L);
  }

  @Test
  @DisplayName("Should throw BAD_GATEWAY when XtmHub client returns false")
  void autoRegister_WhenClientReturnsFalse_ShouldThrowBadGateway() {
    // Given
    String token = "valid-token";
    License license = new License();
    license.setLicenseEnterprise(false);
    mockSettings.setPlatformLicense(license);
    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformName("Test Platform");
    mockSettings.setPlatformBaseUrl("http://localhost");
    mockSettings.setPlatformVersion("1.0.0");
    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(userService.globalCount()).thenReturn((long) 1);
    whenHubAutoRegisters(false);

    // When
    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> xtmHubService.autoRegister(token));

    // Then
    assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
    assertNotNull(exception.getReason());
    assertTrue(exception.getReason().contains("Failed to register"));

    verify(tenantXtmHubRegistrationRepository, never()).save(any(TenantXtmHubRegistration.class));
  }

  @Test
  @DisplayName("Should delete tenant registration when unregister is called")
  void unregister_ShouldDeleteTenantRegistration() {
    // When
    xtmHubService.unregister();

    // Then
    verify(tenantXtmHubRegistrationRepository).deleteByTenantId(Tenant.DEFAULT_TENANT_UUID);
  }
}
