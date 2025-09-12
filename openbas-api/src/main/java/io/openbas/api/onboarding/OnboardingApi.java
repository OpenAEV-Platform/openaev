package io.openbas.api.onboarding;

import io.openbas.api.onboarding.dto.StepsInput;
import io.openbas.database.model.UserOnboardingProgress;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.service.UserService;
import io.openbas.service.onboarding.OnboardingService;
import io.openbas.utils.OnboardingConfig;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OnboardingApi extends RestBehavior {

  public static final String ONBOARDING_URI = "/api/onboarding";

  private final OnboardingService onboardingService;
  private final UserService userService;

  @GetMapping(ONBOARDING_URI)
  @Transactional(readOnly = true)
  @Operation(summary = "Returns the onboarding progress for the authenticated user.")
  public UserOnboardingProgress getOnboardingProgress() {
    return onboardingService.userOnboardingProgress(userService.currentUser());
  }

  @GetMapping(ONBOARDING_URI + "/config")
  @Operation(summary = "Returns the onboarding configuration (categories and steps).")
  public List<OnboardingConfig.OnboardingCategoryDTO> getOnboardingConfig() {
    return OnboardingConfig.getOnboardingConfig();
  }

  @Operation(
      summary =
          "Marks one or more onboarding steps as skipped for the current user and returns the updated progress.")
  @PutMapping(ONBOARDING_URI + "/skipped")
  public UserOnboardingProgress skippedCategory(@RequestBody @NotNull final StepsInput input) {
    return this.onboardingService.skipSteps(userService.currentUser(), input.getSteps());
  }
}
