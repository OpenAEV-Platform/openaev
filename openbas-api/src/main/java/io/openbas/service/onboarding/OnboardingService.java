package io.openbas.service.onboarding;

import static io.openbas.utils.UserOnboardingProgressUtils.initializeDefaults;
import static java.time.Instant.now;

import io.openbas.database.model.User;
import io.openbas.database.model.UserOnboardingProgress;
import io.openbas.database.model.UserOnboardingStepStatus;
import io.openbas.database.repository.UserOnboardingProgressRepository;
import io.openbas.service.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OnboardingService {

  private final UserService userService;
  private final UserOnboardingProgressRepository userOnboardingProgressRepository;

  // -- CRUD --

  public UserOnboardingProgress userOnboardingProgress(@NotNull final User user) {
    return getOrDefault(user);
  }

  public UserOnboardingProgress saveUserOnboardingProgress(
      @NotNull final UserOnboardingProgress userOnboardingProgress) {
    userOnboardingProgress.setUpdateDate(now());
    return userOnboardingProgressRepository.save(userOnboardingProgress);
  }

  // -- ACTION --

  public void completeStep(@NotNull final User user, @NotBlank final String step) {
    UserOnboardingProgress progress = getOrDefault(user);

    UserOnboardingStepStatus stepStatus = progress.getProgressMap().get(step);
    if (stepStatus != null && !stepStatus.isCompleted()) {
      stepStatus.setCompleted(true);
      this.saveUserOnboardingProgress(progress);
    }
  }

  public void completeStepForAllUsers(@NotBlank final String step) {
    List<User> users = userService.users();

    for (User user : users) {
      this.completeStep(user, step);
    }
  }

  public UserOnboardingProgress skipSteps(
      @NotNull final User user, @NotNull final List<String> steps) {
    UserOnboardingProgress progress = getOrDefault(user);

    List<UserOnboardingStepStatus> stepStatuses =
        steps.stream().map(s -> progress.getProgressMap().get(s)).toList();
    if (!stepStatuses.isEmpty()) {
      stepStatuses.stream().filter(s -> !s.isCompleted()).forEach(s -> s.setSkipped(true));
      user.setOnboardingProgress(progress);
      userService.updateUser(user);
    }
    return progress;
  }

  // -- PRIVATE --

  private UserOnboardingProgress getOrDefault(@NotNull final User user) {
    UserOnboardingProgress progress = user.getOnboardingProgress();
    if (progress == null) {
      return initializeDefaults(user);
    }
    return progress;
  }
}
