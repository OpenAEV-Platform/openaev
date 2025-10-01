package io.openaev.executors;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.utils.InjectionUtils.isInInjectableRange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.model.ExecutionProcess;
import io.openaev.service.FileService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public abstract class Injector {

  @Resource protected ObjectMapper mapper;
  private FileService fileService;
  private DocumentRepository documentRepository;

  @Autowired
  public void setDocumentRepository(DocumentRepository documentRepository) {
    this.documentRepository = documentRepository;
  }

  @Autowired
  public void setFileService(FileService fileService) {
    this.fileService = fileService;
  }

  public abstract ExecutionProcess process(Execution execution, ExecutableInject injection)
      throws Exception;

  public StatusPayload getPayloadOutput(String externalId) {
    return null;
  }

  @Transactional
  public Execution execute(ExecutableInject executableInject) {
    Execution execution = new Execution(executableInject.isRuntime());
    try {
      boolean isScheduledInject = !executableInject.isDirect();
      // If empty content, inject must be rejected
      if (executableInject.getInjection().getInject().getContent() == null) {
        throw new UnsupportedOperationException("Inject is empty");
      }
      // If inject is too old, reject the execution
      if (isScheduledInject && !isInInjectableRange(executableInject.getInjection())) {
        throw new UnsupportedOperationException(
            "Inject is now too old for execution: id "
                + executableInject.getInjection().getId()
                + ", launch date "
                + executableInject.getInjection().getDate()
                + ", now date "
                + Instant.now());
      }
      // Process the execution
      ExecutionProcess executionProcess = process(execution, executableInject);
      execution.setAsync(executionProcess.isAsync());
    } catch (Exception e) {
      execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
    } finally {
      execution.stop();
    }
    return execution;
  }

  public Execution executeInjection(ExecutableInject executableInject) {
    return execute(executableInject);
  }

  // region utils

  public <T> T contentConvert(
      @NotNull final ExecutableInject injection, @NotNull final Class<T> converter)
      throws Exception {
    Inject inject = injection.getInjection().getInject();
    ObjectNode content = inject.getContent();
    return this.mapper.treeToValue(content, converter);
  }

  public List<DataAttachment> resolveAttachments(
      Execution execution, ExecutableInject injection, List<Document> documents) {
    List<DataAttachment> resolved = new ArrayList<>();
    // Add attachments from direct configuration
    injection
        .getDirectAttachments()
        .forEach(
            doc -> {
              try {
                byte[] content = IOUtils.toByteArray(doc.getInputStream());
                resolved.add(
                    new DataAttachment(
                        doc.getName(), doc.getOriginalFilename(), content, doc.getContentType()));
              } catch (Exception e) {
                String message = "Error getting direct attachment " + doc.getName();
                execution.addTrace(getNewErrorTrace(message, ExecutionTraceAction.EXECUTION));
              }
            });
    // Add attachments from configuration
    documents.forEach(
        attachment -> {
          String documentId = attachment.getId();
          Optional<Document> askedDocument = documentRepository.findById(documentId);
          try {
            Document doc = askedDocument.orElseThrow();
            InputStream fileInputStream = fileService.getFile(doc).orElseThrow();
            byte[] content = IOUtils.toByteArray(fileInputStream);
            resolved.add(new DataAttachment(documentId, doc.getName(), content, doc.getType()));
          } catch (Exception e) {
            // Can't fetch the attachments, ignore
            String docInfo = askedDocument.map(Document::getName).orElse(documentId);
            String message = "Error getting doc attachment " + docInfo;
            execution.addTrace(getNewErrorTrace(message, ExecutionTraceAction.EXECUTION));
          }
        });
    return resolved;
  }
  // endregion

}
