package io.openbas.injectors.opencti;

import static io.openbas.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openbas.injectors.opencti.OpenCTIContract.OPENCTI_CREATE_CASE;

import io.openbas.database.model.*;
import io.openbas.execution.ExecutableInject;
import io.openbas.executors.Injector;
import io.openbas.injectors.opencti.model.CaseContent;
import io.openbas.injectors.opencti.service.OpenCTIService;
import io.openbas.model.ExecutionProcess;
import io.openbas.model.Expectation;
import io.openbas.model.expectation.ManualExpectation;
import io.openbas.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component(OpenCTIContract.TYPE)
@RequiredArgsConstructor
public class OpenCTIExecutor extends Injector {

  private final OpenCTIService openCTIService;
  private final InjectExpectationService injectExpectationService;

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
