package io.openaev.rest.exercise.service;

import static io.openaev.service.ImportService.EXPORT_ENTRY_ATTACHMENT;
import static io.openaev.service.ImportService.EXPORT_ENTRY_EXERCISE;
import static java.time.Instant.now;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.export.FileExportBase;
import io.openaev.export.Mixins;
import io.openaev.export.WorkflowExportInitializer;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.exports.ExerciseFileExport;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.service.ArticleService;
import io.openaev.service.ChallengeService;
import io.openaev.service.FileService;
import io.openaev.service.chaining.WorkflowOwnerScope;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.organization.OrganizationService;
import jakarta.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ExportService {
  @Resource protected ObjectMapper mapper;
  @Resource private DocumentRepository documentRepository;
  @Resource private ChallengeService challengeService;
  @Resource private ArticleService articleService;
  @Resource private FileService fileService;
  @Resource private WorkflowService workflowService;
  @Resource private WorkflowOwnerScope workflowOwnerScope;
  @Resource private WorkflowExportInitializer workflowExportInitializer;
  @Resource private OrganizationService organizationService;

  public String getZipFileName(
      Exercise exercise, int exportOptionsMask, boolean isChaining, boolean isWithScopeDefinition) {
    String infos;
    if (isChaining) {
      infos =
          "("
              + (ExportOptions.has(ExportOptions.WITH_VARIABLE_VALUES, exportOptionsMask)
                  ? "with_variable_values"
                  : "no_variable_values")
              + " & "
              + (isWithScopeDefinition ? "with_scope_definition" : "no_scope_definition")
              + ")";
    } else {
      infos =
          "("
              + (ExportOptions.has(ExportOptions.WITH_TEAMS, exportOptionsMask)
                  ? "with_teams"
                  : "no_teams")
              + " & "
              + (ExportOptions.has(ExportOptions.WITH_PLAYERS, exportOptionsMask)
                  ? "with_players"
                  : "no_players")
              + " & "
              + (ExportOptions.has(ExportOptions.WITH_VARIABLE_VALUES, exportOptionsMask)
                  ? "with_variable_values"
                  : "no_variable_values")
              + ")";
    }
    return (exercise.getName() + "_" + now().toString()) + "_" + infos + ".zip";
  }

  public byte[] exportExerciseToZip(
      Exercise exercise, int exportOptionsMask, boolean isWithScopeDefinition) throws IOException {
    ObjectMapper objectMapper = mapper.copy();

    ExerciseFileExport importExport =
        ExerciseFileExport.fromExercise(
                exercise,
                objectMapper,
                this.challengeService,
                this.articleService,
                this.organizationService)
            .withOptions(exportOptionsMask);
    // The simulation's workflow is read in the simulation's own tenant whatever the caller's
    // scope, as a workflow inherits its owner's tenant and can never move. Every other table this
    // export reads (documents, tags, challenges, organizations, teams) stays under the scope the
    // request resolved, so the owner scope holds only over the chaining reads below.
    boolean isChaining =
        workflowOwnerScope.call(
            exercise, () -> workflowService.isSimulationChaining(exercise.getId()));
    if (isChaining) {
      importExport.setInjects(new ArrayList<>());
    }

    // Choose workflow mixin based on scope definition option
    objectMapper.addMixIn(
        Workflow.class,
        isWithScopeDefinition
            ? Mixins.WorkflowExport.class
            : Mixins.WorkflowExportWithoutScope.class);

    // Add workflow (chaining) if present — scope definition is optional. The whole workflow graph
    // is loaded inside the owner's tenant, so the serialization below finds it already initialized.
    Optional<Workflow> workflowOpt =
        workflowOwnerScope.call(
            exercise,
            () -> {
              Optional<Workflow> workflow =
                  workflowService.findWorkflowTemplateBySimulationIdForExport(exercise.getId());
              workflow.ifPresent(
                  found -> workflowExportInitializer.initialize(found, isWithScopeDefinition));
              return workflow;
            });
    workflowOpt.ifPresent(
        workflow -> {
          if (isChaining) {
            ArrayList<Tag> workflowAndExerciseTags = new ArrayList<>(importExport.getTags());
            workflowAndExerciseTags.addAll(workflowExportInitializer.collectWorkflowTags(workflow));
            importExport.setTags(workflowAndExerciseTags.stream().distinct().toList());
          }
          importExport.setWorkflow(workflow);
        });

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ZipOutputStream zipExport = new ZipOutputStream(outputStream);
    ZipEntry zipEntry = new ZipEntry(exercise.getName() + ".json");
    zipEntry.setComment(EXPORT_ENTRY_EXERCISE);
    zipExport.putNextEntry(zipEntry);
    ObjectNode exportNode = importExport.getObjectMapper().valueToTree(importExport);
    FileExportBase.dropForeignOrganizationReferences(exportNode, "exercise");
    workflowExportInitializer.enrichWorkflowDataForExport(
        exportNode, "exercise_workflow", importExport.getObjectMapper());
    zipExport.write(
        importExport
            .getObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsBytes(exportNode));
    zipExport.closeEntry();
    // Add the actual files for the documents
    importExport.getAllDocumentIds().stream()
        .distinct()
        .forEach(
            docId -> {
              Document doc =
                  documentRepository.findById(docId).orElseThrow(ElementNotFoundException::new);
              // Include a document's bytes only when it belongs to the exercise's tenant: a
              // document
              // bound from another tenant is skipped, as if the object were missing.
              Optional<InputStream> docStream =
                  fileService.getFile(
                      doc, exercise.getTenant() == null ? null : exercise.getTenant().getId());
              if (docStream.isPresent()) {
                try {
                  ZipEntry zipDoc = new ZipEntry(doc.getTarget());
                  zipDoc.setComment(EXPORT_ENTRY_ATTACHMENT);
                  byte[] data = docStream.get().readAllBytes();
                  zipExport.putNextEntry(zipDoc);
                  zipExport.write(data);
                  zipExport.closeEntry();
                } catch (IOException e) {
                  log.error(e.getMessage(), e);
                }
              }
            });
    zipExport.finish();
    zipExport.close();

    return outputStream.toByteArray();
  }
}
