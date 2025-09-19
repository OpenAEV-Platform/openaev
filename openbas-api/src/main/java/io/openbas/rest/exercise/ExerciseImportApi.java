package io.openbas.rest.exercise;

import static io.openbas.rest.exercise.ExerciseApi.EXERCISE_URI;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.Exercise;
import io.openbas.database.model.ImportMapper;
import io.openbas.database.model.ResourceType;
import io.openbas.database.repository.ImportMapperRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.exception.UnprocessableContentException;
import io.openbas.rest.exercise.service.ExerciseService;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.rest.scenario.form.InjectsImportInput;
import io.openbas.rest.scenario.response.ImportTestSummary;
import io.openbas.service.InjectImportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ExerciseImportApi extends RestBehavior {

  private final InjectImportService injectImportService;
  private final ImportMapperRepository importMapperRepository;
  private final ExerciseService exerciseService;

  @PostMapping(EXERCISE_URI + "/{exerciseId}/xls/{importId}/dry")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Test the import of injects from an xls file")
  public ImportTestSummary dryRunImportXLSFile(
      @PathVariable @NotBlank final String exerciseId,
      @PathVariable @NotBlank final String importId,
      @Valid @RequestBody final InjectsImportInput input) {
    Exercise exercise = this.exerciseService.exercise(exerciseId);

    // Getting the mapper to use
    ImportMapper importMapper =
        this.importMapperRepository
            .findById(UUID.fromString(input.getImportMapperId()))
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        String.format(
                            "The import mapper %s was not found", input.getImportMapperId())));

    return this.injectImportService.importInjectIntoExerciseFromXLS(
        exercise, importMapper, importId, input.getName(), input.getTimezoneOffset(), false);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/xls/{importId}/import")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Validate and import injects from an xls file")
  public ImportTestSummary validateImportXLSFile(
      @PathVariable @NotBlank final String exerciseId,
      @PathVariable @NotBlank final String importId,
      @Valid @RequestBody final InjectsImportInput input) {
    Exercise exercise = this.exerciseService.exercise(exerciseId);

    if (input.getLaunchDate() != null) {
      exercise.setStart(input.getLaunchDate().toInstant());
    }

    // Getting the mapper to use
    ImportMapper importMapper =
        importMapperRepository
            .findById(UUID.fromString(input.getImportMapperId()))
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        String.format(
                            "The import mapper %s was not found", input.getImportMapperId())));

    ImportTestSummary importTestSummary =
        injectImportService.importInjectIntoExerciseFromXLS(
            exercise, importMapper, importId, input.getName(), input.getTimezoneOffset(), true);
    this.exerciseService.updateExercise(exercise);
    return importTestSummary;
  }

  @PostMapping(
      path = EXERCISE_URI + "/{simulationId}/injects/import",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void injectsImport(
      @RequestPart("file") MultipartFile file,
      @PathVariable @NotBlank final String simulationId,
      HttpServletResponse response)
      throws Exception {
    if (file == null || file.isEmpty()) {
      throw new UnprocessableContentException("Insufficient input: file is required");
    }
    this.injectImportService.importInjectsForSimulation(file, simulationId);
  }
}
