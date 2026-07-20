package io.openaev.rest.exercise.service;

import static io.openaev.service.ImportService.EXPORT_ENTRY_ATTACHMENT;
import static io.openaev.service.ImportService.EXPORT_ENTRY_EXERCISE;
import static java.time.Instant.now;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.export.Mixins;
import io.openaev.export.WorkflowExportInitializer;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.exports.ExerciseFileExport;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.service.ArticleService;
import io.openaev.service.ChallengeService;
import io.openaev.service.FileService;
import io.openaev.service.chaining.WorkflowService;
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
  @Resource private WorkflowExportInitializer workflowExportInitializer;

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
                exercise, objectMapper, this.challengeService, this.articleService)
            .withOptions(exportOptionsMask);
    boolean isChaining = workflowService.isSimulationChaining(exercise.getId());
    if (isChaining) {
      importExport.setInjects(new ArrayList<>());
    }

    // Choose workflow mixin based on scope definition option
    objectMapper.addMixIn(
        Workflow.class,
        isWithScopeDefinition
            ? Mixins.WorkflowExport.class
            : Mixins.WorkflowExportWithoutScope.class);

    // Add workflow (chaining) if present — scope definition is optional
    Optional<Workflow> workflowOpt =
        workflowService.findWorkflowTemplateBySimulationIdForExport(exercise.getId());
    workflowOpt.ifPresent(
        workflow -> {
          workflowExportInitializer.initialize(workflow, isWithScopeDefinition);
          importExport.setWorkflow(workflow);
        });

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ZipOutputStream zipExport = new ZipOutputStream(outputStream);
    ZipEntry zipEntry = new ZipEntry(exercise.getName() + ".json");
    zipEntry.setComment(EXPORT_ENTRY_EXERCISE);
    zipExport.putNextEntry(zipEntry);
    ObjectNode exportNode = importExport.getObjectMapper().valueToTree(importExport);
    workflowExportInitializer.enrichWorkflowStepDataForExport(
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
              Optional<InputStream> docStream = fileService.getFile(doc);
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
