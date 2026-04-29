package io.openaev.xtmhub;

import static org.apache.hc.core5.http.HttpHeaders.ACCEPT;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.context.TenantContext;
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
import java.util.List;
import java.util.Map;
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
    registration.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    return registration;
  }

  /**
   * Stubs MockServer to return connectivity statuses for multiple tenants from the all-tenants
   * mutation. The map key is tenantId, value is the status label.
   */
  private void whenHubReturnsAllTenantsConnectivityStatuses(Map<String, String> tenantStatuses) {
    StringBuilder statuses = new StringBuilder();
    tenantStatuses.forEach(
        (tenantId, status) -> {
          if (!statuses.isEmpty()) statuses.append(",");
          statuses.append("{\"tenantId\":\"%s\",\"status\":\"%s\"}".formatted(tenantId, status));
        });
    mockServer
        .when(request().withMethod("POST").withPath(GRAPHQL_PATH))
        .respond(
            response()
                .withStatusCode(200)
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withBody(
                    "{\"data\":{\"refreshPlatformRegistrationConnectivityStatusAllTenants\":{\"statuses\":[%s]}}}"
                        .formatted(statuses)));
  }

  @Nested
  @DisplayName("refreshConnectivity")
  class RefreshConnectivity {

    @Test
    @DisplayName("Should call XTM Hub refresh endpoint when registration is present")
    void whenRegistrationIsPresent_ShouldCallXtmHub() {
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
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);
      whenHubReturnsConnectivityStatus("active");

      // When
      xtmHubService.refreshConnectivity();

      // Then
      verifyRefreshConnectivityRequest(platformId, platformVersion, token, platformBaseUrl);
    }

    @Test
    @DisplayName("Should return null when no registration exists")
    void whenRegistrationIsAbsent_ShouldReturnNull() {
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
    void whenPlatformIsNotFound_ShouldRemoveRegistration() {
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
      verify(tenantXtmHubRegistrationRepository).deleteByTenantId(any());
      verifyNoInteractions(xtmHubEmailService);
    }

    @Test
    @DisplayName("Should update registration as REGISTERED when connectivity is ACTIVE")
    void whenConnectivityIsActive_ShouldUpdateAsRegistered() {
      // Given
      String token = "valid-token";
      LocalDateTime lastCheck = now.minusHours(12);

      TenantXtmHubRegistration registration = buildRegistration(token, lastCheck);
      when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
          .thenReturn(Optional.of(registration));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");

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

      verify(platformSettingsService, never()).updateXTMHubEmailNotification(anyBoolean());
      verifyNoInteractions(xtmHubEmailService);
    }

    @Test
    @DisplayName("Should update registration as LOST_CONNECTIVITY when connectivity is inactive")
    void whenConnectivityIsInactive_ShouldUpdateAsLostConnectivity() {
      // Given
      String token = "valid-token";
      LocalDateTime lastCheck = now.minusHours(12);

      TenantXtmHubRegistration registration = buildRegistration(token, lastCheck);
      when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
          .thenReturn(Optional.of(registration));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");

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

      verifyNoInteractions(xtmHubEmailService);
      verify(platformSettingsService, never()).updateXTMHubEmailNotification(anyBoolean());
    }

    @Test
    @DisplayName("Should handle null lastConnectivityCheck by using current time")
    void whenLastConnectivityCheckIsNull_ShouldUseCurrentTime() {
      // Given
      TenantXtmHubRegistration registration = buildRegistration("valid-token", null);
      when(tenantXtmHubRegistrationRepository.findByTenantId(any()))
          .thenReturn(Optional.of(registration));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");

      when(platformSettingsService.findSettings()).thenReturn(mockSettings);
      whenHubReturnsConnectivityStatus("inactive");
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // When
      TenantXtmHubRegistration result = xtmHubService.refreshConnectivity();

      // Then
      assertNotNull(result);
      verifyNoInteractions(xtmHubEmailService);
      verify(platformSettingsService, never()).updateXTMHubEmailNotification(anyBoolean());
    }
  }

  /** Returns the parsed GraphQL body for the all-tenants request. */
  private JsonObject getAllTenantsRequestBody() {
    var recorded =
        mockServer.retrieveRecordedRequests(request().withMethod("POST").withPath(GRAPHQL_PATH));
    assertThat(recorded).hasSize(1);
    return JsonParser.parseString(recorded[0].getBodyAsString()).getAsJsonObject();
  }

  @Nested
  @DisplayName("refreshConnectivityAllTenants")
  class RefreshConnectivityAllTenants {

    @Test
    @DisplayName("Should do nothing when no registrations exist")
    void whenNoRegistrations_ShouldDoNothing() {
      // Given
      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted()).thenReturn(List.of());

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then
      verifyNoRequestSentToHub();
      verifyNoInteractions(xtmHubEmailService);
      verify(platformSettingsService, never()).updateXTMHubEmailNotification(anyBoolean());
      verify(tenantXtmHubRegistrationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should save active tenants as REGISTERED and delete NOT_FOUND tenants")
    void whenMixedStatuses_ShouldSaveActiveAndDeleteNotFound() {
      // Given
      TenantContext.setCurrentTenant("tenant-active");
      TenantXtmHubRegistration activeReg = buildRegistration("token-1", now.minusHours(1));
      TenantContext.setCurrentTenant("tenant-not-found");
      TenantXtmHubRegistration notFoundReg = buildRegistration("token-2", now.minusHours(1));
      String activeTenantId = activeReg.getTenant().getId();
      String notFoundTenantId = notFoundReg.getTenant().getId();

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted())
          .thenReturn(List.of(activeReg, notFoundReg));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("true");
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(
          Map.of(activeTenantId, "active", notFoundTenantId, "not_found"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then
      verify(tenantXtmHubRegistrationRepository).deleteByTenantId(notFoundTenantId);
      verify(tenantXtmHubRegistrationRepository, never()).deleteByTenantId(activeTenantId);

      ArgumentCaptor<TenantXtmHubRegistration> captor =
          ArgumentCaptor.forClass(TenantXtmHubRegistration.class);
      verify(tenantXtmHubRegistrationRepository, times(1)).save(captor.capture());
      assertThat(captor.getValue().getRegistrationStatus())
          .isEqualTo(XtmHubRegistrationStatus.REGISTERED);

      verifyNoInteractions(xtmHubEmailService);
      verify(platformSettingsService, times(1)).updateXTMHubEmailNotification(true);
    }

    @Test
    @DisplayName(
        "Should send email and update flag when tenant lost connectivity for more than 24h")
    void whenTenantLostConnectivityMoreThan24h_ShouldSendEmail() {
      // Given
      TenantContext.setCurrentTenant("tenant-1");
      LocalDateTime lastCheck = now.minusHours(25);
      TenantXtmHubRegistration reg = buildRegistration("token-1", lastCheck);
      String tenantId = reg.getTenant().getId();

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted()).thenReturn(List.of(reg));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("true");
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(Map.of(tenantId, "inactive"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then
      verify(xtmHubEmailService).sendLostConnectivityEmail();
      verify(platformSettingsService).updateXTMHubEmailNotification(false);

      ArgumentCaptor<TenantXtmHubRegistration> captor =
          ArgumentCaptor.forClass(TenantXtmHubRegistration.class);
      verify(tenantXtmHubRegistrationRepository).save(captor.capture());
      assertThat(captor.getValue().getRegistrationStatus())
          .isEqualTo(XtmHubRegistrationStatus.LOST_CONNECTIVITY);
      assertThat(captor.getValue().getLastConnectivityCheck()).isEqualTo(lastCheck);
    }

    @Test
    @DisplayName("Should not send email when tenant lost connectivity for less than 24h")
    void whenTenantLostConnectivityLessThan24h_ShouldNotSendEmail() {
      // Given
      TenantContext.setCurrentTenant("tenant-1");
      LocalDateTime lastCheck = now.minusHours(12);
      TenantXtmHubRegistration reg = buildRegistration("token-1", lastCheck);
      String tenantId = reg.getTenant().getId();

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted()).thenReturn(List.of(reg));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("true");
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(Map.of(tenantId, "inactive"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then — connectivity is still lost, threshold not reached: flag is not touched
      verifyNoInteractions(xtmHubEmailService);
      verify(platformSettingsService, never()).updateXTMHubEmailNotification(anyBoolean());

      ArgumentCaptor<TenantXtmHubRegistration> captor =
          ArgumentCaptor.forClass(TenantXtmHubRegistration.class);
      verify(tenantXtmHubRegistrationRepository).save(captor.capture());
      assertThat(captor.getValue().getRegistrationStatus())
          .isEqualTo(XtmHubRegistrationStatus.LOST_CONNECTIVITY);
    }

    @Test
    @DisplayName("Should not send email when connectivity is lost but email sending is disabled")
    void whenEmailSendingIsDisabled_ShouldNotSendEmail() {
      // Given
      TenantContext.setCurrentTenant("tenant-1");
      LocalDateTime lastCheck = now.minusHours(25);
      TenantXtmHubRegistration reg = buildRegistration("token-1", lastCheck);
      String tenantId = reg.getTenant().getId();

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted()).thenReturn(List.of(reg));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("false");
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(Map.of(tenantId, "inactive"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then — connectivity is still lost, flag already false: flag is not touched
      verifyNoInteractions(xtmHubEmailService);
      verify(platformSettingsService, never()).updateXTMHubEmailNotification(anyBoolean());
    }

    @Test
    @DisplayName("Should default to INACTIVE when hub does not return a status for a tenant")
    void whenHubMissesTenant_ShouldDefaultToInactive() {
      // Given
      TenantContext.setCurrentTenant("tenant-1");
      TenantXtmHubRegistration reg = buildRegistration("token-1", now.minusHours(1));

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted()).thenReturn(List.of(reg));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("true");
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      // Hub returns statuses for a different tenant only
      whenHubReturnsAllTenantsConnectivityStatuses(Map.of("other-tenant", "active"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then
      ArgumentCaptor<TenantXtmHubRegistration> captor =
          ArgumentCaptor.forClass(TenantXtmHubRegistration.class);
      verify(tenantXtmHubRegistrationRepository).save(captor.capture());
      assertThat(captor.getValue().getRegistrationStatus())
          .isEqualTo(XtmHubRegistrationStatus.LOST_CONNECTIVITY);
    }

    @Test
    @DisplayName(
        "Should send a single email when ALL tenants have lost connectivity for more than 24h")
    void whenAllTenantsLostConnectivityMoreThan24h_ShouldSendEmailOnce() {
      // Given
      TenantContext.setCurrentTenant("tenant-1");
      TenantXtmHubRegistration reg1 = buildRegistration("token-1", now.minusHours(25));
      TenantContext.setCurrentTenant("tenant-2");
      TenantXtmHubRegistration reg2 = buildRegistration("token-2", now.minusHours(30));

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted())
          .thenReturn(List.of(reg1, reg2));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("true");
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(
          Map.of("tenant-1", "inactive", "tenant-2", "inactive"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then — email sent exactly once, not once per tenant
      verify(xtmHubEmailService, times(1)).sendLostConnectivityEmail();
      verify(platformSettingsService).updateXTMHubEmailNotification(false);
    }

    @Test
    @DisplayName(
        "Should not send email when only some tenants have lost connectivity — not all of them")
    void whenOnlySomeTenantsLostConnectivity_ShouldNotSendEmail() {
      // Given
      TenantContext.setCurrentTenant("tenant-1");
      TenantXtmHubRegistration reg1 = buildRegistration("token-1", now.minusHours(25));
      TenantContext.setCurrentTenant("tenant-2");
      TenantXtmHubRegistration reg2 = buildRegistration("token-2", now.minusHours(1));

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted())
          .thenReturn(List.of(reg1, reg2));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("true");
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(
          Map.of("tenant-1", "inactive", "tenant-2", "active"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then — one tenant is still active, so no email should be sent
      verifyNoInteractions(xtmHubEmailService);
      verify(platformSettingsService).updateXTMHubEmailNotification(true);
    }

    @Test
    @DisplayName(
        "Should not send email again and not reset flag when all tenants are still lost and email was already sent")
    void whenAllTenantsStillLostAndEmailAlreadySent_ShouldNotSendEmailAgain() {
      // Given — flag=false simulates the email was already sent on a previous run
      TenantContext.setCurrentTenant("tenant-1");
      TenantXtmHubRegistration reg = buildRegistration("token-1", now.minusHours(30));
      String tenantId = reg.getTenant().getId();

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted()).thenReturn(List.of(reg));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("false"); // already sent, flag disabled
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(Map.of(tenantId, "inactive"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then — no new email, and the flag is not touched (connectivity is still lost)
      verifyNoInteractions(xtmHubEmailService);
      verify(platformSettingsService, never()).updateXTMHubEmailNotification(anyBoolean());
    }

    @Test
    @DisplayName("Should reset the email flag when connectivity is restored after having been lost")
    void whenConnectivityRestoredAfterLoss_ShouldResetEmailFlag() {
      // Given — flag=false simulates the email was sent on a previous run
      TenantContext.setCurrentTenant("tenant-1");
      TenantXtmHubRegistration reg = buildRegistration("token-1", now.minusHours(30));
      String tenantId = reg.getTenant().getId();

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted()).thenReturn(List.of(reg));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("false"); // was disabled after loss
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(Map.of(tenantId, "active")); // now restored

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then — flag re-armed, no email
      verifyNoInteractions(xtmHubEmailService);
      verify(platformSettingsService).updateXTMHubEmailNotification(true);
    }

    @Test
    @DisplayName("Should send email when no registrations exist after filtering NOT_FOUND")
    void whenAllRegistrationsAreNotFound_ShouldNotSendEmail() {
      // Given
      TenantContext.setCurrentTenant("tenant-1");
      TenantXtmHubRegistration reg = buildRegistration("token-1", now.minusHours(30));
      String tenantId = reg.getTenant().getId();

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted()).thenReturn(List.of(reg));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("true");
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(Map.of(tenantId, "not_found"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then — registration deleted, checkResults is empty, no email
      verify(tenantXtmHubRegistrationRepository).deleteByTenantId(tenantId);
      verifyNoInteractions(xtmHubEmailService);
      verify(platformSettingsService, never()).updateXTMHubEmailNotification(anyBoolean());
    }

    @Test
    @DisplayName("Should send correct url per tenant in the GraphQL request body")
    void whenRegistrationsExist_ShouldSendCorrectUrlPerTenant() {
      // Given
      TenantContext.setCurrentTenant("tenant-1");
      TenantXtmHubRegistration reg1 = buildRegistration("token-1", now.minusHours(1));
      TenantContext.setCurrentTenant("tenant-2");
      TenantXtmHubRegistration reg2 = buildRegistration("token-2", now.minusHours(1));

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted())
          .thenReturn(List.of(reg1, reg2));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("true");
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(
          Map.of("tenant-1", "active", "tenant-2", "active"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then
      JsonArray tenants =
          getAllTenantsRequestBody()
              .getAsJsonObject("variables")
              .getAsJsonObject("input")
              .getAsJsonArray("tenants");

      assertThat(tenants).hasSize(2);
      tenants.forEach(
          element -> {
            JsonObject entry = element.getAsJsonObject();
            String tenantId = entry.get("tenantId").getAsString();
            String expectedUrl = "http://localhost/" + tenantId;
            assertThat(entry.get("url").getAsString()).isEqualTo(expectedUrl);
          });
    }

    @Test
    @DisplayName("Should ignore soft-deleted tenants and not call hub for them")
    void whenTenantIsSoftDeleted_ShouldNotBeIncludedInRefresh() {
      // Given — the repository already excludes soft-deleted tenants,
      // so only the non-deleted registration is returned.
      TenantContext.setCurrentTenant("tenant-active");
      TenantXtmHubRegistration activeReg = buildRegistration("token-active", now.minusHours(1));

      when(tenantXtmHubRegistrationRepository.findAllByTenantNotDeleted())
          .thenReturn(List.of(activeReg));
      when(tenantXtmHubRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      mockSettings.setPlatformId("platform-123");
      mockSettings.setPlatformVersion("1.0.0");
      mockSettings.setPlatformBaseUrl("http://localhost");
      mockSettings.setXtmHubShouldSendConnectivityEmail("true");
      when(platformSettingsService.findSettings()).thenReturn(mockSettings);

      whenHubReturnsAllTenantsConnectivityStatuses(Map.of("tenant-active", "active"));

      // When
      xtmHubService.refreshConnectivityAllTenants();

      // Then — only 1 tenant sent to hub (soft-deleted tenant is absent from the payload)
      JsonArray tenants =
          getAllTenantsRequestBody()
              .getAsJsonObject("variables")
              .getAsJsonObject("input")
              .getAsJsonArray("tenants");
      assertThat(tenants).hasSize(1);
      assertThat(tenants.get(0).getAsJsonObject().get("tenantId").getAsString())
          .isEqualTo("tenant-active");

      verify(tenantXtmHubRegistrationRepository, times(1)).save(any());
      verify(tenantXtmHubRegistrationRepository, never()).deleteByTenantId("tenant-deleted");
    }
  }

  @Nested
  @DisplayName("autoRegister")
  class AutoRegister {

    @Test
    @DisplayName("Should compute contract level as CE for non-enterprise license")
    void withNonEnterpriseLicense_ShouldUseCEContract() {
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
    void withEnterpriseTrialLicense_ShouldUseTrialContract() {
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
      assertThat(input.getAsJsonObject("platform").get("contract").getAsString())
          .isEqualTo("trial");
    }

    @Test
    @DisplayName("Should compute contract level as EE for enterprise license")
    void withEnterpriseStandardLicense_ShouldUseEEContract() {
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
    void whenSuccessful_ShouldUpdateRegistrationStatus() {
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
      ArgumentCaptor<TenantXtmHubRegistration> captor =
          ArgumentCaptor.forClass(TenantXtmHubRegistration.class);
      verify(tenantXtmHubRegistrationRepository).save(captor.capture());
      TenantXtmHubRegistration saved = captor.getValue();
      assertThat(saved.getToken()).isEqualTo(token);
      assertThat(saved.getRegistrationStatus()).isEqualTo(XtmHubRegistrationStatus.REGISTERED);
      assertThat(saved.getRegistrationDate()).isNotNull();
      assertThat(saved.getLastConnectivityCheck()).isNotNull();
    }

    @Test
    @DisplayName("Should send correct platform payload to XTM Hub")
    void whenSuccessful_ShouldSendCorrectPayloadToHub() {
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
      assertThat(platform.get("tenantId").getAsString()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
      assertThat(input.get("existing_users_count").getAsLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should throw BAD_GATEWAY when XtmHub client returns false")
    void whenClientReturnsFalse_ShouldThrowBadGateway() {
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
  }

  @Nested
  @DisplayName("unregister")
  class Unregister {

    @Test
    @DisplayName("Should delete tenant registration")
    void shouldDeleteTenantRegistration() {
      // When
      xtmHubService.unregister();

      // Then
      verify(tenantXtmHubRegistrationRepository).deleteByTenantId(Tenant.DEFAULT_TENANT_UUID);
    }
  }
}
