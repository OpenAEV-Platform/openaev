package io.openaev.injectors.opencti;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.injectors.opencti.OpenCTIContract.OPENCTI_CREATE_CASE;

import io.openaev.database.model.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Injector;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.opencti.model.CaseContent;
import io.openaev.model.ExecutionProcess;
import io.openaev.model.Expectation;
import io.openaev.model.expectation.ManualExpectation;
import io.openaev.opencti.service.OpenCTIService;
import io.openaev.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;

public class OpenCTIExecutor extends Injector {

  private final OpenCTIService openCTIService;
  private final InjectExpectationService injectExpectationService;

  public OpenCTIExecutor(
      InjectorContext context,
      OpenCTIService openCTIService,
      InjectExpectationService injectExpectationService) {
    super(context);
    this.openCTIService = openCTIService;
    this.injectExpectationService = injectExpectationService;
  }

  private void createCase(
      Execution execution, String name, String description, List<DataAttachment> attachments) {
    try {
      openCTIService.createCase(execution, name, description, attachments);
    } catch (Exception e) {
      execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
    }
  }

  private void createReport(
      Execution execution, String name, String description, List<DataAttachment> attachments) {
    try {
      openCTIService.createReport(execution, name, description, attachments);
    } catch (Exception e) {
      execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
    }
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection)
      throws Exception {
    Inject inject = injection.getInjection().getInject();
    CaseContent content = contentConvert(injection, CaseContent.class);
    List<Document> documents =
        inject.getDocuments().stream()
            .filter(InjectDocument::isAttached)
            .map(InjectDocument::getDocument)
            .toList();
    List<DataAttachment> attachments = resolveAttachments(execution, injection, documents);
    String name = content.getName();
    String description = content.getDescription();

    inject
        .getInjectorContract()
        .ifPresent(
            injectorContract -> {
              switch (injectorContract.getId()) {
                case OPENCTI_CREATE_CASE -> createCase(execution, name, description, attachments);
                default -> createReport(execution, name, description, attachments);
              }
            });

    List<Expectation> expectations =
        content.getExpectations().stream()
            .flatMap(
                (entry) ->
                    switch (entry.getType()) {
                      case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                      default -> Stream.of();
                    })
            .toList();

    injectExpectationService.buildAndSaveInjectExpectations(injection, expectations);

    return new ExecutionProcess(false);
  }
}
