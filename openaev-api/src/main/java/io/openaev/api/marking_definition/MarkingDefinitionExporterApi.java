package io.openaev.api.marking_definition;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.MapperService;
import io.openaev.service.marking_definition.MarkingDefinitionService;
import io.openaev.service.marking_definition.MarkingDefinitionStixService;
import io.openaev.utils.CsvType;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping({MarkingDefinitionApi.TENANT_MARKING_DEFINITIONS_URI})
@Tag(
    name = "Marking definition exporter API",
    description = "Operations related to the export of marking definitions")
public class MarkingDefinitionExporterApi extends RestBehavior {

  private final MarkingDefinitionService service;
  private final MapperService mapperService;
  private final MarkingDefinitionStixService stixService;

  // -- STIX (OpenCTI format) --

  @LogExecutionTime
  @GetMapping("/stix")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(
      summary = "Export marking definitions in OpenCTI's STIX 2.1 format",
      description =
          "A STIX 2.1 bundle of marking-definition objects, each with the STIX id OpenCTI gives"
              + " the marking and OpenCTI's order and color.")
  public ObjectNode exportStix(TxCtx ctx) {
    return stixService.exportBundle(ctx);
  }

  // -- CSV --

  @LogExecutionTime
  @PostMapping("/export/csv")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.MARKING_DEFINITION)
  @Operation(
      summary = "Export marking definitions as CSV",
      description =
          "Exports the marking definitions matching the search as CSV; answers 404 while the"
              + " marking preview feature is off.")
  public void exportMarkingDefinitionsCsv(
      TxCtx ctx,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput,
      HttpServletResponse response) {
    service.assertFeatureEnabled();
    // The shared writer sets only the content type: without this the response stays ISO-8859-1,
    // which garbles accents and turns other scripts into '?' in a file the importer reads as UTF-8.
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    mapperService.exportMappersCsv(
        ctx, CsvType.MARKING_DEFINITIONS, searchPaginationInput, response);
  }
}
