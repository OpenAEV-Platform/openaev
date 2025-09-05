package io.openbas.service;

import static io.openbas.helper.StreamHelper.fromIterable;

import io.openbas.database.model.Grant;
import io.openbas.database.model.GrantableBase;
import io.openbas.database.model.Group;
import io.openbas.database.model.User;
import io.openbas.database.repository.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
public class GrantService {

  private final GroupRepository groupRepository;
  private final GrantRepository grantRepository;
  private final ExerciseRepository exerciseRepository;
  private final ScenarioRepository scenarioRepository;
  private final InjectRepository injectRepository;
  private final PayloadRepository payloadRepository;

  public <T extends GrantableBase> void computeGrant(@NotNull T resource) {
    // Extract grant resource type
    Grant.GRANT_RESOURCE_TYPE grantResourceType =
        GrantableBase.getGrantResourceType(resource.getClass());
    // Find automatic groups to grants
    List<Group> groups = fromIterable(this.groupRepository.findAll());
    List<Grant> grants =
        groups.stream()
            .filter(
                group -> {
                  List<Grant.GRANT_TYPE> defaultGrants =
                      group.getDefaultGrantsMap().get(grantResourceType);
                  return defaultGrants != null && !defaultGrants.isEmpty();
                })
            .flatMap(
                group ->
                    group.getDefaultGrantsMap().get(grantResourceType).stream()
                        .distinct()
                        .map(s -> Tuples.of(group, s)))
            .map(
                tuple -> {
                  Grant grant = new Grant();
                  grant.setGroup(tuple.getT1());
                  grant.setName(tuple.getT2());
                  grant.setResourceId(resource.getId());
                  grant.setGrantResourceType(grantResourceType);
                  return grant;
                })
            .toList();

    if (!grants.isEmpty()) {
      Iterable<Grant> savedGrants = this.grantRepository.saveAll(grants);
      resource.setGrants(fromIterable(savedGrants));
    }
  }

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
