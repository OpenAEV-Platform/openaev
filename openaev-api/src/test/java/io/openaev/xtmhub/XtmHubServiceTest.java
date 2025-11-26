package io.openaev.xtmhub;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.xtmhub.config.XtmHubConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XtmHubServiceTest {

  @Mock private PlatformSettingsService platformSettingsService;

  @Mock private XtmHubClient xtmHubClient;

  @Mock private XtmHubConfig xtmHubConfig;

  @Mock private XtmHubEmailService xtmHubEmailService;

  @InjectMocks private XtmHubService xtmHubService;

  private PlatformSettings mockSettings;
  private LocalDateTime now;
  private LocalDateTime registrationDate;

  @BeforeEach
  void setUp() {
    mockSettings = new PlatformSettings();
    now = LocalDateTime.now();
    registrationDate = now.minusDays(5);
  }

  @Test
  @DisplayName("Should return settings unchanged when XTM Hub token is blank")
  void refreshConnectivity_WhenTokenIsBlank_ShouldReturnSettingsUnchanged() {
    // Given
    mockSettings.setXtmHubToken("");
    when(platformSettingsService.findSettings()).thenReturn(mockSettings);

    // When
    PlatformSettings result = xtmHubService.refreshConnectivity();

    // Then
    assertEquals(mockSettings, result);
    verify(platformSettingsService).findSettings();
    verifyNoInteractions(xtmHubClient);
    verifyNoInteractions(xtmHubEmailService);
    verify(platformSettingsService, never())
        .updateXTMHubRegistration(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should return settings unchanged when XTM Hub token is null")
  void refreshConnectivity_WhenTokenIsNull_ShouldReturnSettingsUnchanged() {
    // Given
    mockSettings.setXtmHubToken(null);
    when(platformSettingsService.findSettings()).thenReturn(mockSettings);

    // When
    PlatformSettings result = xtmHubService.refreshConnectivity();

    // Then
    assertEquals(mockSettings, result);
    verify(platformSettingsService).findSettings();
    verifyNoInteractions(xtmHubClient);
    verifyNoInteractions(xtmHubEmailService);
  }

  @Test
  @DisplayName("Should remove XTM Hub registration when platform is not found in the hub")
  void refreshConnectivity_WhenPlatformIsNotFound_ShouldRemoveRegistration() {
    // Given
    String token = "valid-token";
    String platformId = "platform-123";
    String platformVersion = "1.0.0";

    mockSettings.setXtmHubToken(token);
    mockSettings.setPlatformId(platformId);
    mockSettings.setPlatformVersion(platformVersion);

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(xtmHubClient.refreshRegistrationStatus(platformId, platformVersion, token))
        .thenReturn(XtmHubConnectivityStatus.NOT_FOUND);

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
    String platformId = "platform-123";
    String platformVersion = "1.0.0";
    String userId = "user-123";
    String userName = "John Doe";
    LocalDateTime lastCheck = now.minusHours(12);

    mockSettings.setXtmHubToken(token);
    mockSettings.setPlatformId(platformId);
    mockSettings.setPlatformVersion(platformVersion);
    mockSettings.setXtmHubRegistrationDate(registrationDate.toString());
    mockSettings.setXtmHubRegistrationUserId(userId);
    mockSettings.setXtmHubRegistrationUserName(userName);
    mockSettings.setXtmHubLastConnectivityCheck(lastCheck.toString());
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    PlatformSettings updatedSettings = new PlatformSettings();

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(xtmHubClient.refreshRegistrationStatus(platformId, platformVersion, token))
        .thenReturn(XtmHubConnectivityStatus.ACTIVE);
    when(platformSettingsService.updateXTMHubRegistration(
            eq(token),
            eq(registrationDate),
            eq(XtmHubRegistrationStatus.REGISTERED),
            eq(new XtmHubRegistererRecord(userId, userName)),
            any(LocalDateTime.class),
            eq(true)))
        .thenReturn(updatedSettings);

    // When
    PlatformSettings result = xtmHubService.refreshConnectivity();

    // Then
    assertEquals(updatedSettings, result);
    verify(xtmHubClient).refreshRegistrationStatus(platformId, platformVersion, token);
    verify(platformSettingsService)
        .updateXTMHubRegistration(
            eq(token),
            eq(registrationDate),
            eq(XtmHubRegistrationStatus.REGISTERED),
            eq(new XtmHubRegistererRecord(userId, userName)),
            any(LocalDateTime.class),
            eq(true));
    verifyNoInteractions(xtmHubEmailService);
  }

  @Test
  @DisplayName(
      "Should update registration as LOST_CONNECTIVITY when connectivity is not ACTIVE and not send email if less than 24 hours")
  void refreshConnectivity_WhenConnectivityLostLessThan24Hours_ShouldNotSendEmail() {
    // Given
    String token = "valid-token";
    LocalDateTime lastCheck = now.minusHours(12);

    mockSettings.setXtmHubToken(token);
    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setXtmHubRegistrationDate(registrationDate.toString());
    mockSettings.setXtmHubRegistrationUserId("user-123");
    mockSettings.setXtmHubRegistrationUserName("John Doe");
    mockSettings.setXtmHubLastConnectivityCheck(lastCheck.toString());
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    PlatformSettings updatedSettings = new PlatformSettings();

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(xtmHubClient.refreshRegistrationStatus(anyString(), anyString(), anyString()))
        .thenReturn(XtmHubConnectivityStatus.INACTIVE);
    when(platformSettingsService.updateXTMHubRegistration(any(), any(), any(), any(), any(), any()))
        .thenReturn(updatedSettings);

    // When
    PlatformSettings result = xtmHubService.refreshConnectivity();

    // Then
    assertEquals(updatedSettings, result);
    verify(platformSettingsService)
        .updateXTMHubRegistration(
            eq(token),
            eq(registrationDate),
            eq(XtmHubRegistrationStatus.LOST_CONNECTIVITY),
            eq(new XtmHubRegistererRecord("user-123", "John Doe")),
            eq(lastCheck),
            eq(true));
    verifyNoInteractions(xtmHubEmailService);
  }

  @Test
  @DisplayName("Should not send connectivity email when email is disabled from configuration")
  void refreshConnectivity_WhenEmailDisabledFromConfig_ShouldNotSendEmail() {
    // Given
    when(xtmHubConfig.getConnectivityEmailEnable()).thenReturn(false);
    String token = "valid-token";
    LocalDateTime lastCheck = now.minusHours(25);

    mockSettings.setXtmHubToken(token);
    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setXtmHubRegistrationDate(registrationDate.toString());
    mockSettings.setXtmHubRegistrationUserId("user-123");
    mockSettings.setXtmHubRegistrationUserName("John Doe");
    mockSettings.setXtmHubLastConnectivityCheck(lastCheck.toString());
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    PlatformSettings updatedSettings = new PlatformSettings();

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(xtmHubClient.refreshRegistrationStatus(anyString(), anyString(), anyString()))
        .thenReturn(XtmHubConnectivityStatus.INACTIVE);
    when(platformSettingsService.updateXTMHubRegistration(any(), any(), any(), any(), any(), any()))
        .thenReturn(updatedSettings);

    // When
    PlatformSettings result = xtmHubService.refreshConnectivity();

    // Then
    assertEquals(updatedSettings, result);
    verifyNoInteractions(xtmHubEmailService);
  }

  @Test
  @DisplayName(
      "Should send connectivity email when connectivity is lost for more than 24 hours and email sending is enabled")
  void refreshConnectivity_WhenConnectivityLostMoreThan24HoursAndEmailEnabled_ShouldSendEmail() {
    // Given
    when(xtmHubConfig.getConnectivityEmailEnable()).thenReturn(true);
    String token = "valid-token";
    LocalDateTime lastCheck = now.minusHours(25);

    mockSettings.setXtmHubToken(token);
    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setXtmHubRegistrationDate(registrationDate.toString());
    mockSettings.setXtmHubRegistrationUserId("user-123");
    mockSettings.setXtmHubRegistrationUserName("John Doe");
    mockSettings.setXtmHubLastConnectivityCheck(lastCheck.toString());
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    PlatformSettings updatedSettings = new PlatformSettings();

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(xtmHubClient.refreshRegistrationStatus(anyString(), anyString(), anyString()))
        .thenReturn(XtmHubConnectivityStatus.INACTIVE);
    when(platformSettingsService.updateXTMHubRegistration(any(), any(), any(), any(), any(), any()))
        .thenReturn(updatedSettings);

    // When
    PlatformSettings result = xtmHubService.refreshConnectivity();

    // Then
    assertEquals(updatedSettings, result);
    verify(xtmHubEmailService).sendLostConnectivityEmail();
    verify(platformSettingsService)
        .updateXTMHubRegistration(
            eq(token),
            eq(registrationDate),
            eq(XtmHubRegistrationStatus.LOST_CONNECTIVITY),
            eq(new XtmHubRegistererRecord("user-123", "John Doe")),
            eq(lastCheck),
            eq(false));
  }

  @Test
  @DisplayName("Should not send email when connectivity is lost but email sending is disabled")
  void refreshConnectivity_WhenConnectivityLostButEmailDisabled_ShouldNotSendEmail() {
    // Given
    String token = "valid-token";
    LocalDateTime lastCheck = now.minusHours(25);

    mockSettings.setXtmHubToken(token);
    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setXtmHubRegistrationDate(registrationDate.toString());
    mockSettings.setXtmHubRegistrationUserId("user-123");
    mockSettings.setXtmHubRegistrationUserName("John Doe");
    mockSettings.setXtmHubLastConnectivityCheck(lastCheck.toString());
    mockSettings.setXtmHubShouldSendConnectivityEmail("false");

    PlatformSettings updatedSettings = new PlatformSettings();

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(xtmHubClient.refreshRegistrationStatus(anyString(), anyString(), anyString()))
        .thenReturn(XtmHubConnectivityStatus.INACTIVE);
    when(platformSettingsService.updateXTMHubRegistration(any(), any(), any(), any(), any(), any()))
        .thenReturn(updatedSettings);

    // When
    PlatformSettings result = xtmHubService.refreshConnectivity();

    // Then
    assertEquals(updatedSettings, result);
    verifyNoInteractions(xtmHubEmailService);
    verify(platformSettingsService)
        .updateXTMHubRegistration(
            eq(token),
            eq(registrationDate),
            eq(XtmHubRegistrationStatus.LOST_CONNECTIVITY),
            eq(new XtmHubRegistererRecord("user-123", "John Doe")),
            eq(lastCheck),
            eq(true));
  }

  @Test
  @DisplayName("Should handle null lastConnectivityCheck by using current time")
  void refreshConnectivity_WhenLastConnectivityCheckIsNull_ShouldUseCurrentTime() {
    // Given
    String token = "valid-token";

    mockSettings.setXtmHubToken(token);
    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setXtmHubRegistrationDate(registrationDate.toString());
    mockSettings.setXtmHubRegistrationUserId("user-123");
    mockSettings.setXtmHubRegistrationUserName("John Doe");
    mockSettings.setXtmHubLastConnectivityCheck(null);
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    PlatformSettings updatedSettings = new PlatformSettings();

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(xtmHubClient.refreshRegistrationStatus(anyString(), anyString(), anyString()))
        .thenReturn(XtmHubConnectivityStatus.INACTIVE);
    when(platformSettingsService.updateXTMHubRegistration(any(), any(), any(), any(), any(), any()))
        .thenReturn(updatedSettings);

    // When
    PlatformSettings result = xtmHubService.refreshConnectivity();

    // Then
    assertEquals(updatedSettings, result);
    verifyNoInteractions(
        xtmHubEmailService); // Should not send email as it's considered first check
    verify(platformSettingsService)
        .updateXTMHubRegistration(
            eq(token),
            eq(registrationDate),
            eq(XtmHubRegistrationStatus.LOST_CONNECTIVITY),
            eq(new XtmHubRegistererRecord("user-123", "John Doe")),
            any(LocalDateTime.class),
            eq(true));
  }

  @Test
  @DisplayName("Should handle exactly 24 hours difference")
  void refreshConnectivity_WhenExactly24HoursPassed_ShouldSendEmail() {
    // Given
    when(xtmHubConfig.getConnectivityEmailEnable()).thenReturn(true);
    String token = "valid-token";
    LocalDateTime lastCheck = now.minusHours(24);

    mockSettings.setXtmHubToken(token);
    mockSettings.setPlatformId("platform-123");
    mockSettings.setPlatformVersion("1.0.0");
    mockSettings.setXtmHubRegistrationDate(registrationDate.toString());
    mockSettings.setXtmHubRegistrationUserId("user-123");
    mockSettings.setXtmHubRegistrationUserName("John Doe");
    mockSettings.setXtmHubLastConnectivityCheck(lastCheck.toString());
    mockSettings.setXtmHubShouldSendConnectivityEmail("true");

    PlatformSettings updatedSettings = new PlatformSettings();

    when(platformSettingsService.findSettings()).thenReturn(mockSettings);
    when(xtmHubClient.refreshRegistrationStatus(anyString(), anyString(), anyString()))
        .thenReturn(XtmHubConnectivityStatus.INACTIVE);
    when(platformSettingsService.updateXTMHubRegistration(any(), any(), any(), any(), any(), any()))
        .thenReturn(updatedSettings);

    // When
    PlatformSettings result = xtmHubService.refreshConnectivity();

    // Then
    assertEquals(updatedSettings, result);
    verify(xtmHubEmailService).sendLostConnectivityEmail();
    verify(platformSettingsService)
        .updateXTMHubRegistration(
            eq(token),
            eq(registrationDate),
            eq(XtmHubRegistrationStatus.LOST_CONNECTIVITY),
            eq(new XtmHubRegistererRecord("user-123", "John Doe")),
            eq(lastCheck),
            eq(false));
  }
}
