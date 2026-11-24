package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
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
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A simulation reset clears its object storage under its own tenant's prefix and, for the
 * compatibility period, under the default tenant's prefix too (the layout attachments were written
 * to before #7961).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Resetting a simulation clears its object storage under both layouts")
class ExerciseResetStorageLayoutsTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private FileService fileService;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String resetTenantId;

  @AfterEach
  void cleanup() {
    tenantHelper.deleteCommittedTenants(resetTenantId);
    resetTenantId = null;
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName(
      "given_closedSimulationOfAnotherTenant_when_reset_should_deleteObjectsUnderBothStorageLayouts")
  void
      given_closedSimulationOfAnotherTenant_when_reset_should_deleteObjectsUnderBothStorageLayouts()
          throws Exception {
    // Arrange - a simulation closed in a tenant of its own, with an object under its own tenant
    // prefix and one under the default tenant's prefix (the earlier storage layout).
    Tenant tenant = tenantHelper.createTenant("reset-storage-layouts");
    resetTenantId = tenant.getId();
    tenantHelper.attachCurrentUserToTenant(resetTenantId);

    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setTenant(tenant);
    exercise.setStatus(ExerciseStatus.CANCELED);
    exercise.setStart(Instant.now());
    String exerciseId = exerciseRepository.saveAndFlush(exercise).getId();

    String ownLayoutFile =
        fileService.uploadStream(
            resetTenantId,
            "/" + exerciseId,
            "report.txt",
            new ByteArrayInputStream("own-layout".getBytes(StandardCharsets.UTF_8)));
    String legacyLayoutFile =
        fileService.uploadStream(
            Tenant.DEFAULT_TENANT_UUID,
            "/" + exerciseId,
            "report.txt",
            new ByteArrayInputStream("legacy-layout".getBytes(StandardCharsets.UTF_8)));

    // An unrelated simulation's object, under the same default prefix, must survive the reset
    String otherExerciseId = UUID.randomUUID().toString();
    String otherFile =
        fileService.uploadStream(
            Tenant.DEFAULT_TENANT_UUID,
            "/" + otherExerciseId,
            "report.txt",
            new ByteArrayInputStream("unrelated".getBytes(StandardCharsets.UTF_8)));

    ExerciseUpdateStatusInput input = new ExerciseUpdateStatusInput();
    input.setStatus(ExerciseStatus.SCHEDULED);

    // Act
    mvc.perform(
            put(TENANT_EXERCISE_URI + "/{exerciseId}/status", resetTenantId, exerciseId)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    // Assert - both layouts are gone, the unrelated simulation's object is not
    assertThat(fileService.getFileContainer(resetTenantId, ownLayoutFile)).isEmpty();
    assertThat(fileService.getFileContainer(Tenant.DEFAULT_TENANT_UUID, legacyLayoutFile))
        .isEmpty();
    assertThat(fileService.getFileContainer(Tenant.DEFAULT_TENANT_UUID, otherFile)).isPresent();
  }
}
