package io.openbas.xtmhub;

import io.openbas.database.model.User;
import io.openbas.rest.settings.response.PlatformSettings;
import io.openbas.service.PlatformSettingsService;
import io.openbas.service.UserService;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@AllArgsConstructor
public class XtmHubService {
  private final PlatformSettingsService platformSettingsService;
  private final UserService userService;

  public PlatformSettings register(@NotBlank final String token) {
    User currentUser = userService.currentUser();
    return this.platformSettingsService.updateXTMHubRegistration(
        token,
        LocalDateTime.now(),
        XtmHubRegistrationStatus.REGISTERED,
        currentUser.getId(),
        currentUser.getName());
  }

  public PlatformSettings unregister() {
    return this.platformSettingsService.updateXTMHubRegistration(
        null, null, XtmHubRegistrationStatus.UNREGISTERED, null, null);
  }
}
