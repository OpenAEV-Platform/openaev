package io.openaev.xtmhub;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.xtmhub.config.XtmHubConfig;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class XtmHubStartupAutoRegisterServiceTest {

  @Mock private XtmHubService xtmHubService;
  @Mock private XtmHubConnectivityService xtmHubConnectivityService;

  private XtmHubStartupAutoRegisterService serviceWith(XtmHubConfig config) {
    return new XtmHubStartupAutoRegisterService(xtmHubService, config, xtmHubConnectivityService);
  }

  @Nested
  @DisplayName("init")
  class Init {

    @Test
    void given_missingPlatformToken_should_notAutoRegister() {
      // Arrange
      XtmHubConfig config = new XtmHubConfig();
      config.setEnable(true);

      // Act
      serviceWith(config).init();

      // Assert
      verify(xtmHubConnectivityService, never()).isReachable();
      verify(xtmHubService, never()).getRegistration();
      verify(xtmHubService, never()).autoRegister(anyString());
    }

    @Test
    void given_xtmHubDisabled_should_notAutoRegister() {
      // Arrange
      XtmHubConfig config = new XtmHubConfig();
      config.setEnable(false);
      config.setPlatformToken("platform-token");

      // Act
      serviceWith(config).init();

      // Assert
      verify(xtmHubConnectivityService, never()).isReachable();
      verify(xtmHubService, never()).getRegistration();
      verify(xtmHubService, never()).autoRegister(anyString());
    }

    @Test
    void given_xtmHubUnreachable_should_notAutoRegister() {
      // Arrange
      XtmHubConfig config = new XtmHubConfig();
      config.setEnable(true);
      config.setPlatformToken("platform-token");
      when(xtmHubConnectivityService.isReachable()).thenReturn(false);

      // Act
      serviceWith(config).init();

      // Assert
      verify(xtmHubService, never()).getRegistration();
      verify(xtmHubService, never()).autoRegister(anyString());
    }

    @Test
    void given_existingRegistration_should_notAutoRegister() {
      // Arrange
      XtmHubConfig config = new XtmHubConfig();
      config.setEnable(true);
      config.setPlatformToken("platform-token");
      when(xtmHubConnectivityService.isReachable()).thenReturn(true);
      when(xtmHubService.getRegistration()).thenReturn(Optional.of(new TenantXtmHubRegistration()));

      // Act
      serviceWith(config).init();

      // Assert
      verify(xtmHubService, times(1)).getRegistration();
      verify(xtmHubService, never()).autoRegister(anyString());
    }

    @Test
    void given_startupReady_should_autoRegister() {
      // Arrange
      XtmHubConfig config = new XtmHubConfig();
      config.setEnable(true);
      config.setPlatformToken("platform-token");
      when(xtmHubConnectivityService.isReachable()).thenReturn(true);
      when(xtmHubService.getRegistration()).thenReturn(Optional.empty());

      // Act
      serviceWith(config).init();

      // Assert
      verify(xtmHubService, times(1)).autoRegister("platform-token");
    }

    @Test
    void given_autoRegisterFailure_should_notCrashStartup() {
      // Arrange
      XtmHubConfig config = new XtmHubConfig();
      config.setEnable(true);
      config.setPlatformToken("platform-token");
      when(xtmHubConnectivityService.isReachable()).thenReturn(true);
      when(xtmHubService.getRegistration()).thenReturn(Optional.empty());
      doThrow(new RuntimeException("deployment request missing"))
          .when(xtmHubService)
          .autoRegister("platform-token");

      // Act / Assert
      assertDoesNotThrow(() -> serviceWith(config).init());
      verify(xtmHubService, times(1)).autoRegister("platform-token");
    }
  }
}
