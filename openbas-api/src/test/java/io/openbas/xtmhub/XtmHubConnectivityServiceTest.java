package io.openbas.xtmhub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import io.openbas.xtmhub.config.XtmHubConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class XtmHubConnectivityServiceTest {
  @Mock private XtmHubConfig xtmHubConfig;

  private XtmHubConnectivityService createServiceWithMockedHttp() {
    return mock(
        XtmHubConnectivityService.class,
        withSettings().useConstructor(xtmHubConfig).defaultAnswer(CALLS_REAL_METHODS));
  }

  @Test
  void testInitWhenEnabledAndReachable() {
    // Given
    when(xtmHubConfig.getEnable()).thenReturn(true);
    XtmHubConnectivityService service = createServiceWithMockedHttp();
    doReturn(true).when(service).checkIsReachable();

    service.init();

    assertTrue(service.isReachable());
  }

  @Test
  void testInitWhenEnabledButNotReachable() {
    // Given
    when(xtmHubConfig.getEnable()).thenReturn(true);
    XtmHubConnectivityService service = createServiceWithMockedHttp();
    doReturn(false).when(service).checkIsReachable();

    service.init();

    assertFalse(service.isReachable());
  }

  @Test
  void testInitWhenDisabled() {
    // Given
    when(xtmHubConfig.getEnable()).thenReturn(false);
    XtmHubConnectivityService service = createServiceWithMockedHttp();

    service.init();

    assertFalse(service.isReachable());
    verify(service, never()).checkIsReachable();
  }
}
