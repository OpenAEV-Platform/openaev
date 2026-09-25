package io.openaev.rest.inject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.inject.service.InjectExportService;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inject export bundles a document's bytes under a single owning tenant, derived from the
 * injects being exported. A real export always comes from one exercise or scenario, hence one
 * tenant. A set spanning tenants is only reachable by a crafted cross-tenant selection; picking the
 * first inject's tenant would silently drop the other tenants' attachments while leaving their ids
 * in the JSON, so a multi-tenant export is refused outright. A single-tenant export still succeeds.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Inject export refuses a set of injects spanning multiple tenants")
class InjectExportMultiTenantRefusalTest extends IntegrationTest {

  @Autowired private InjectExportService injectExportService;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectRepository injectRepository;

  @BeforeEach
  void reset() {
    injectComposer.reset();
    injectorContractComposer.reset();
    exerciseComposer.reset();
  }

  @Test
  @DisplayName("given_injectsAcrossTwoTenants_should_refuseTheExport")
  void given_injectsAcrossTwoTenants_should_refuseTheExport() throws Exception {
    // Arrange: two persisted injects. The second's tenant is overridden in memory to another tenant
    // so the set spans two. The override is not persisted (tenant_id is updatable=false, and it
    // also
    // takes part in the injects composite foreign keys), which is exactly the point: the refusal
    // reads the in-memory entity's tenant, no DB write needed.
    Inject injectOne = persistInject();
    Inject injectTwo = persistInject();
    injectTwo.setTenant(new Tenant(UUID.randomUUID().toString()));
    List<Inject> injects = List.of(injectOne, injectTwo);

    // Act & Assert
    assertThrows(
        BadRequestException.class,
        () -> injectExportService.exportInjectsToZip(injects, 0),
        "an export of injects spanning two tenants must be refused");
  }

  @Test
  @DisplayName("given_injectsWithinASingleTenant_should_exportSuccessfully")
  void given_injectsWithinASingleTenant_should_exportSuccessfully() throws Exception {
    // Arrange: two injects in the same tenant, the normal single-tenant export.
    Inject injectOne = persistInject();
    Inject injectTwo = persistInject();

    List<Inject> injects =
        injectRepository.findAllById(List.of(injectOne.getId(), injectTwo.getId()));

    // Act & Assert
    assertDoesNotThrow(() -> injectExportService.exportInjectsToZip(injects, 0));
  }

  private Inject persistInject() {
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectorContract(
            injectorContractComposer.forInjectorContract(
                InjectorContractFixture.createDefaultInjectorContract()))
        .withExercise(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .persist()
        .get();
  }
}
