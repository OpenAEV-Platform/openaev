package io.openaev.service;

import io.openaev.database.model.*;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Grant.GRANT_RESOURCE_TYPE;
import io.openaev.database.model.Grant.GRANT_TYPE;
import io.openaev.database.model.User;
import io.openaev.database.repository.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GrantService {

  private final GrantRepository grantRepository;
  private final ExerciseRepository exerciseRepository;
  private final ScenarioRepository scenarioRepository;
  private final InjectRepository injectRepository;
  private final PayloadRepository payloadRepository;

  public boolean hasReadGrant(@NotBlank final String resourceId, @NotNull final User user) {
    return hasGrant(resourceId, user, GRANT_TYPE.OBSERVER);
  }

  public boolean hasWriteGrant(@NotBlank final String resourceId, @NotNull final User user) {
    return hasGrant(resourceId, user, GRANT_TYPE.PLANNER);
  }

  public boolean hasLaunchGrant(@NotBlank final String resourceId, @NotNull final User user) {
    return hasGrant(resourceId, user, GRANT_TYPE.LAUNCHER);
  }

  private boolean hasGrant(
      @NotBlank final String resourceId,
      @NotNull final User user,
      @NotNull final GRANT_TYPE grantType) {
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
      @NotBlank String currentId, @NotBlank String newId, @NotBlank GRANT_RESOURCE_TYPE grantType) {
    grantRepository.updateGrantResourceIdAndType(currentId, newId, grantType);
  }

  // -- CRUD --

  public Grant createGrant(
      @NotNull GRANT_TYPE name,
      Group group,
      @NotBlank String resourceId,
      @NotNull GRANT_RESOURCE_TYPE resourceType) {
    Grant grant = new Grant();
    grant.setName(name);
    grant.setGroup(group);
    grant.setResourceId(resourceId);
    grant.setGrantResourceType(resourceType);
    return grantRepository.save(grant);
  }

  public List<Grant> duplicateGrants(
      @NotNull List<Grant> sourceGrants,
      @NotBlank String targetResourceId,
      @NotNull GRANT_RESOURCE_TYPE targetResourceType) {
    return new ArrayList<>(
        sourceGrants.stream()
            .map(
                originalGrant ->
                    createGrant(
                        originalGrant.getName(),
                        originalGrant.getGroup(),
                        targetResourceId,
                        targetResourceType))
            .toList());
  }
}
