package io.openaev.xtmone;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
@DisplayName("XtmOneConnectivityService")
class XtmOneConnectivityServiceTest {

  @Mock private XtmOneConfig config;
  @Mock private XtmOneService xtmOneService;
  @Mock private XtmOneIdentity xtmOneIdentity;
  @Mock private TaskScheduler taskScheduler;

  @InjectMocks private XtmOneConnectivityService connectivityService;

  @Test
  @DisplayName("each tick registers and refreshes the identity the browser is handed")
  void tickRegistersAndRefreshesTheIdentity() {
    connectivityService.tick();

    verify(xtmOneService).autoRegister();
    verify(xtmOneIdentity).publishedIssuer();
  }

  @Test
  @DisplayName("a failed registration still refreshes the identity")
  void failedRegistrationStillRefreshesTheIdentity() {
    doThrow(new IllegalStateException("XTM One unreachable")).when(xtmOneService).autoRegister();

    connectivityService.tick();

    verify(xtmOneIdentity).publishedIssuer();
  }
}
