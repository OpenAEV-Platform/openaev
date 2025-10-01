package io.openaev.service;

import io.openaev.database.model.*;
import io.openaev.database.model.Grant;
import io.openaev.database.model.User;
import io.openaev.database.repository.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GrantService {

  private final GroupRepository groupRepository;
  private final GrantRepository grantRepository;
  private final ExerciseRepository exerciseRepository;
  private final ScenarioRepository scenarioRepository;
  private final InjectRepository injectRepository;
  private final PayloadRepository payloadRepository;

  public boolean hasReadGrant(@NotBlank final String resourceId, @NotNull final User user) {
    return hasGrant(resourceId, user, Grant.GRANT_TYPE.OBSERVER);
  }

  public boolean hasWriteGrant(@NotBlank final String resourceId, @NotNull final User user) {
    return hasGrant(resourceId, user, Grant.GRANT_TYPE.PLANNER);
  }

  public boolean hasLaunchGrant(@NotBlank final String resourceId, @NotNull final User user) {
    return hasGrant(resourceId, user, Grant.GRANT_TYPE.LAUNCHER);
  }

  private boolean hasGrant(
      @NotBlank final String resourceId,
      @NotNull final User user,
      @NotNull final Grant.GRANT_TYPE grantType) {
    return this.grantRepository.existsByUserIdAndResourceIdAndNameIn(
        user.getId(), resourceId, grantType.andHigher());
  }

  /**
   * Validates that the resource ID is not blank and exists in one of the grantable resource
   * repositories.
   *
   * @param resourceId the resource ID to validate
   * @throws IllegalArgumentException if the resource ID is blank or does not exist
   */
  public void validateResourceIdForGrant(String resourceId) {
    if (StringUtils.isBlank(resourceId)) {
      throw new IllegalArgumentException("A valid resource ID should be present");
    }

    boolean exists =
        exerciseRepository.existsById(resourceId)
            || scenarioRepository.existsById(resourceId)
            // Atomic testings:
            || injectRepository.existsByIdAndScenarioIsNullAndExerciseIsNull(resourceId)
            || payloadRepository.existsById(resourceId);

    if (!exists) {
      throw new IllegalArgumentException("A valid resource ID should be present");
    }
  }

  public void updateGrantsForNewResource(
      @NotBlank String currentId,
      @NotBlank String newId,
      @NotBlank Grant.GRANT_RESOURCE_TYPE grantType) {
    grantRepository.updateGrantResourceIdAndType(currentId, newId, grantType);
  }
}
