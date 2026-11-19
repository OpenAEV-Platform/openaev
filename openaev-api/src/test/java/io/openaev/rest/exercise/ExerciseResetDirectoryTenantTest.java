package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.rest.exercise.form.ExerciseUpdateStatusInput;
import io.openaev.service.FileService;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The simulation reset's storage delete (#7961) runs in an {@code afterCommit} callback, after the
 * request's transaction and its scope are both gone, so it is only correct if it captured the
 * simulation's own tenant beforehand rather than reading it from the ambient {@code TenantContext}
 * at that later point ({@code ExerciseService#changeExerciseStatus}, the {@code
 * fileService.deleteDirectoryForTenant} call). The non-prefixed route never sets that ambient
 * thread-local at all, so for a caller of several tenants it falls back to the default tenant,
 * which can differ from the simulation's own: exactly the gap a revert to the ambient-based {@code
 * FileService#deleteDirectory} would reopen, with nothing here going red first.
 *
 * <p>{@code @Transactional(propagation = NOT_SUPPORTED)} on the test method suspends the class's
 * own rollback-only transaction so the request's transaction commits for real, the only way the
 * {@code afterCommit} callback fires at all.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A simulation reset deletes its storage directory under its own tenant")
class ExerciseResetDirectoryTenantTest extends IntegrationTest {

  private static final byte[] CONTENT = "attachment".getBytes(StandardCharsets.UTF_8);

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private FileService fileService;
  @Autowired private MinioService minioService;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String otherTenant;

  @BeforeEach
  void seedOtherTenantAndJoinBoth() throws Exception {
    otherTenant = tenantHelper.createTenantWithCurrentUser("exercise-reset-directory-o").getId();
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName(
      "given a caller of both tenants and a cancelled simulation of the other one when the"
          + " non-prefixed route resets it then its storage directory is deleted under its own"
          + " tenant, and the earlier layout's default-tenant prefix is cleared too")
  void given_ambientFallsBackToDefault_should_deleteUnderTheSimulationsOwnTenant()
      throws Exception {
    // -- Arrange: a cancelled simulation of the other tenant, a file under its own tenant path
    // and a file under the default tenant's path, the earlier storage layout and what an
    // ambient-based delete would target, since the non-prefixed route never sets the v1 ambient
    // tenant, which then falls back to the default one. The reset clears both layouts.
    String exerciseId = saveCanceledExercise(otherTenant);
    uploadFileUnderBothLayouts(exerciseId);

    ExerciseUpdateStatusInput input = new ExerciseUpdateStatusInput();
    input.setStatus(ExerciseStatus.SCHEDULED);

    try {
      // -- Act: no X-Tenant-Ids header, so the ctx scope is the caller's whole membership (both
      // tenants), wide enough that the simulation's own tenant is found and the read succeeds.
      mvc.perform(
              put(EXERCISE_URI + "/" + exerciseId + "/status")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- Assert --
      assertThat(minioService.countObjects(otherTenant + "/" + exerciseId))
          .as("deleted under the simulation's own tenant")
          .isZero();
      assertThat(minioService.countObjects(Tenant.DEFAULT_TENANT_UUID + "/" + exerciseId))
          .as(
              "the earlier layout's default-tenant prefix is cleared during the compatibility period")
          .isZero();
    } finally {
      exerciseRepository.deleteById(exerciseId);
      minioService.deleteDirectoryForTenant(otherTenant, exerciseId);
      minioService.deleteDirectoryForTenant(Tenant.DEFAULT_TENANT_UUID, exerciseId);
    }
  }

  private String saveCanceledExercise(String tenantId) {
    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setName("exercise-reset-directory-" + UUID.randomUUID());
    exercise.setTenant(new Tenant(tenantId));
    exercise.setStatus(ExerciseStatus.CANCELED);
    return exerciseRepository.save(exercise).getId();
  }

  private void uploadFileUnderBothLayouts(String exerciseId) throws Exception {
    fileService.uploadFile(
        otherTenant,
        exerciseId + "/communications/c1/file.txt",
        new ByteArrayInputStream(CONTENT),
        CONTENT.length,
        "text/plain");
    fileService.uploadFile(
        Tenant.DEFAULT_TENANT_UUID,
        exerciseId + "/communications/c1/file.txt",
        new ByteArrayInputStream(CONTENT),
        CONTENT.length,
        "text/plain");
  }
}
