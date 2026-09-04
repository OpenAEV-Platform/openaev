package io.openaev.rest.exercise;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.Exercise;
import io.openaev.ee.EnterpriseEditionException;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.ee.License;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.chaining.WorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExerciseApi - duplicate")
class ExerciseApiDuplicateUnitTest {

  private static final String EXERCISE_ID = "exercise-id";

  @Mock private ExerciseService exerciseService;
  @Mock private WorkflowService workflowService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;

  @InjectMocks private ExerciseApi exerciseApi;

  @Nested
  @DisplayName("Given a time-based simulation")
  class GivenATimeBasedSimulation {

    @Test
    @DisplayName("Given a simulation with no workflow, should duplicate metadata only")
    void given_a_simulation_with_no_workflow_should_duplicate_metadata_only() {
      // -- ARRANGE --
      Exercise duplicate = new Exercise();
      when(exerciseService.getDuplicateExercise(EXERCISE_ID)).thenReturn(duplicate);
      when(workflowService.isSimulationChaining(EXERCISE_ID)).thenReturn(false);

      // -- ACT --
      Exercise result = exerciseApi.duplicateExercise(EXERCISE_ID);

      // -- ASSERT --
      assertSame(duplicate, result);
      verify(workflowService, never())
          .duplicateSimulationWorkflow(anyString(), any(Exercise.class));
      // The licence is only relevant to the chained branch: a time-based simulation must stay
      // duplicable on a Community platform.
      verifyNoInteractions(enterpriseEditionService, licenseCacheManager);
    }
  }

  @Nested
  @DisplayName("Given a chained simulation")
  class GivenAChainedSimulation {

    @Test
    @DisplayName("Given an active license, should also duplicate the logic map")
    void given_an_active_license_should_also_duplicate_the_logic_map() {
      // -- ARRANGE --
      Exercise duplicate = new Exercise();
      License license = new License();
      when(exerciseService.getDuplicateExercise(EXERCISE_ID)).thenReturn(duplicate);
      when(workflowService.isSimulationChaining(EXERCISE_ID)).thenReturn(true);
      when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(license);
      when(enterpriseEditionService.isEnterpriseLicenseInactive(license)).thenReturn(false);

      // -- ACT --
      Exercise result = exerciseApi.duplicateExercise(EXERCISE_ID);

      // -- ASSERT --
      assertSame(duplicate, result);
      verify(workflowService).duplicateSimulationWorkflow(EXERCISE_ID, duplicate);
    }

    @Test
    @DisplayName("Given an inactive license, should reject instead of losing the logic map")
    void given_an_inactive_license_should_reject_instead_of_losing_the_logic_map() {
      // -- ARRANGE --
      License license = new License();
      when(exerciseService.getDuplicateExercise(EXERCISE_ID)).thenReturn(new Exercise());
      when(workflowService.isSimulationChaining(EXERCISE_ID)).thenReturn(true);
      when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(license);
      when(enterpriseEditionService.isEnterpriseLicenseInactive(license)).thenReturn(true);

      // -- ACT & ASSERT --
      assertThrows(
          EnterpriseEditionException.class, () -> exerciseApi.duplicateExercise(EXERCISE_ID));
      verify(workflowService, never())
          .duplicateSimulationWorkflow(anyString(), any(Exercise.class));
    }
  }
}
