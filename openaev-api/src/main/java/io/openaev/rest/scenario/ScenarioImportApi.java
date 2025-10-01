package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ImportMapper;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.UnprocessableContentException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.scenario.form.InjectsImportInput;
import io.openaev.rest.scenario.response.ImportTestSummary;
import io.openaev.service.InjectImportService;
import io.openaev.service.ScenarioService;
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
public class ScenarioImportApi extends RestBehavior {

  private final InjectService injectService;
  private final InjectImportService injectImportService;
  private final ImportMapperRepository importMapperRepository;
  private final ScenarioService scenarioService;

  @PostMapping(SCENARIO_URI + "/{scenarioId}/xls/{importId}/dry")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Test the import of injects from an xls file")
  public ImportTestSummary dryRunImportXLSFile(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String importId,
      @Valid @RequestBody final InjectsImportInput input) {
    Scenario scenario = scenarioService.scenario(scenarioId);

    // Getting the mapper to use
    ImportMapper importMapper =
        importMapperRepository
            .findById(UUID.fromString(input.getImportMapperId()))
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        String.format(
                            "The import mapper %s was not found", input.getImportMapperId())));

    return injectImportService.importInjectIntoScenarioFromXLS(
        scenario, importMapper, importId, input.getName(), input.getTimezoneOffset(), false);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/xls/{importId}/import")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  @Operation(summary = "Validate and import injects from an xls file")
  public ImportTestSummary validateImportXLSFile(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String importId,
      @Valid @RequestBody final InjectsImportInput input) {
    Scenario scenario = scenarioService.scenario(scenarioId);

    if (input.getLaunchDate() != null) {
      scenario.setRecurrenceStart(input.getLaunchDate().toInstant());
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
        injectImportService.importInjectIntoScenarioFromXLS(
            scenario, importMapper, importId, input.getName(), input.getTimezoneOffset(), true);
    scenarioService.updateScenario(scenario);
    return importTestSummary;
  }

  @PostMapping(
      path = SCENARIO_URI + "/{scenarioId}/injects/import",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void injectsImport(
      @RequestPart("file") MultipartFile file,
      @PathVariable @NotBlank final String scenarioId,
      HttpServletResponse response)
      throws Exception {
    if (file == null || file.isEmpty()) {
      throw new UnprocessableContentException("Insufficient input: file is required");
    }
    this.injectImportService.importInjectsForScenario(file, scenarioId);
  }
}
