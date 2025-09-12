package io.openbas.xtmhub;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.ResourceType;
import io.openbas.database.model.User;
import io.openbas.rest.settings.response.PlatformSettings;
import io.openbas.service.PlatformSettingsService;
import io.openbas.service.UserService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class XtmHubService {
  private PlatformSettingsService platformSettingsService;
  private UserService userService;

  @Autowired
  public void setPlatformSettingsService(PlatformSettingsService platformSettingsService) {
    this.platformSettingsService = platformSettingsService;
  }

  @Autowired
  public void setUserService(UserService userService) {
    this.userService = userService;
  }

  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.XTM_HUB_SETTINGS)
  public PlatformSettings register(@NotBlank final String token) {
    User currentUser = userService.currentUser();
    return this.platformSettingsService.updateXTMHubRegistration(
      token,
      LocalDateTime.now(),
      XtmHubRegistrationStatus.REGISTERED,
      currentUser.getId(),
      currentUser.getName()
    );
  }

  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.XTM_HUB_SETTINGS)
  public PlatformSettings unregister() {
    return this.platformSettingsService.updateXTMHubRegistration(
      null,
      null,
      XtmHubRegistrationStatus.UNREGISTERED,
      null,
      null
    );
  }
}
