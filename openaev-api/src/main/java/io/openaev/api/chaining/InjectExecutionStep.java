package io.openaev.api.chaining;

import static io.openaev.service.chaining.StepService.setField;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.Gson;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Executor;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.*;
import io.openaev.utils.TargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class InjectExecutionStep implements ActionStep {
  private static final Gson gson = new Gson();
  private final InjectorContractService injectorContractService;
  private final UserService userService;
  private final AssetService assetService;
  private final TeamService teamService;
  private final TagService tagService;
  private final DocumentService documentService;
  private final InjectService injectService;
  private final TagRuleService tagRuleService;
  private final AssetGroupService assetGroupService;
  private final InjectorContractContentUtils injectorContractContentUtils;
  private final Executor executor;
  private final InjectStatusService injectStatusService;
  @PersistenceContext private EntityManager em;

  @Override
  public Step create(StepsCreateInput.StepCreateInput step, Workflow workflow) {
    String data = this.stepData(step, workflow.getSimulation());
    String input = this.stepInput(step.conditions);
    String outputParser = this.stepOutputParser(data);
    Step stepTemplate =
        Step.builder()
            .data(data)
            .input(input)
            .output_parser(outputParser)
            .status(STEP_STATUS.TEMPLATE)
            .stepAction(STEP_ACTION_CLASS.INJECT_EXECUTION)
            .limitExecution(step.limitExecution)
            .workflow(workflow)
            .build();
    return stepTemplate;
  }

  @Override
  public Step wait(Step stepTemplate, String input) {
    // CALL BY methode update() or by start simulation
    // Verif condition
    // Creation step WAIT add to Queue or Table Queue
    Step waitStep = new Step();
    waitStep.setWorkflow(stepTemplate.getWorkflow());
    waitStep.setData(stepTemplate.getData());
    waitStep.setStepTemplate(stepTemplate);
    // TODO after output paser fromPayload or nuclei or nmap
    waitStep.setInput(input);
    waitStep.setStatus(STEP_STATUS.WAIT);
    waitStep.setStepAction(STEP_ACTION_CLASS.INJECT_EXECUTION);
    waitStep.setLimitExecution(stepTemplate.getLimitExecution());

    return waitStep;
  }

  @Override
  public Step run(Step waitStep, Workflow workflow) {
    // CALL BY QUEUE WAIT
    // Get params
    ObjectMapper om =
        new ObjectMapper()
            .findAndRegisterModules()
            .setInjectableValues(new InjectableValues.Std().addValue(EntityManager.class, em))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try {
      Inject inject = om.readValue(waitStep.getData(), Inject.class);
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(waitStep.getData());

      // Récupérer l'ID de l'injector
      JsonNode injectorNode =
          root.path("inject_injector_contract").path("injector_contract_injector");
      if (!injectorNode.isMissingNode()) {
        String injectorId = injectorNode.asText();

        // Récupérer l'entité via l'EntityManager
        Injector injector = em.find(Injector.class, injectorId);
        inject.getInjectorContract().get().setInjector(injector);
      }

      inject = injectService.createInject(inject);

      ExecutableInject executableInject =
          new ExecutableInject(
              true,
              true,
              inject,
              inject.getTeams(),
              inject.getAssets(),
              inject.getAssetGroups(),
              List.of()); // TODO Check users?

      // TODO Check Pass documents? Executable Payloads
      // executableInject.addDirectAttachment(inject.getDocuments());

      // Execute Inject
      try {
        executor.directExecute(executableInject);
        String data = setInjectId(inject.getId(), waitStep.getData());
        waitStep.setData(data);
        return waitStep;
      } catch (Exception e) {
        log.warn(e.getMessage(), e);
        injectStatusService.failInjectStatus(inject.getId(), e.getMessage());
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  @Override
  public void update(StepsCreateInput.StepCreateInput step, Workflow workflow) {
    // Get output from inject
    // Save new output
    // check if "next" steps need this output
    // If step find, test condition and all input needed and all combinaison
    // call methode wait on step template -> Creation step status wait + update input ...
  }

  @Override
  public void end(StepsCreateInput.StepCreateInput step, Workflow workflow) {
    // Condition de fin step
    // Get all step with id workflow = X if all end workflow = END;
  }

  private String stepData(StepsCreateInput.StepCreateInput step, Exercise exercise) {

    InjectInput data = (InjectInput) step.dataStep;
    // TODO throw exception
    if (data.getInjectorContract() == null) return null;
    InjectorContract injectorContract =
        this.injectorContractService.injectorContract(data.getInjectorContract());
    Inject inject = data.toInject(injectorContract);
    inject.setUser(this.userService.currentUser());

    inject.setTeams(teamService.getTeamsByIds(data.getTeams()));
    inject.setAssets(assetService.assets(data.getAssets()));

    inject.setTags(tagService.tagSet(data.getTagIds()));

    List<InjectDocument> injectDocuments =
        data.getDocuments().stream()
            .map(i -> i.toDocument(documentService.document(i.getDocumentId()), inject))
            .toList();
    inject.setDocuments(injectDocuments);
    Set<Tag> tags = new HashSet<>();
    // TODO Scenario or EXERCISE
    if (exercise != null) {
      tags = exercise.getTags();
      inject.setExercise(exercise);
      // Linked documents directly to the exercise
      inject
          .getDocuments()
          .forEach(
              document -> {
                if (!document.getDocument().getExercises().contains(exercise)) {
                  exercise.getDocuments().add(document.getDocument());
                }
              });
    }
    // verify if inject is not manual/sms/emails...
    if (injectService.canApplyTargetType(inject, TargetType.ASSETS_GROUPS)) {
      // add default asset groups
      inject.setAssetGroups(
          this.tagRuleService.applyTagRuleToInjectCreation(
              tags.stream().map(Tag::getId).toList(),
              assetGroupService.assetGroups(data.getAssetGroups())));
    }

    // if inject content is null we add the defaults from the injector contract
    // this is the case when creating an inject from OpenCti
    if (inject.getContent() == null || inject.getContent().isEmpty()) {
      inject.setContent(
          injectorContractContentUtils.getDynamicInjectorContractFieldsForInject(injectorContract));
    }
    ObjectMapper om =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    try {
      return om.writeValueAsString(inject);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private String stepOutputParser(String data) {
    // inject.getPayload().get().getOutputParsers();
    // Nmap
    // Nuclei
    return "{}";
  }

  private String stepInput(List<ConditionCreateInput> conditions) {
    if (conditions.isEmpty()) return "{}";
    List<Map<String, Object>> inputs = new ArrayList<>();

    for (ConditionCreateInput condition : conditions) {
      if (CONDITION_TYPE.MAPPER.equals(condition.getType())) {

        Map<String, Object> input = new HashMap<>();
        input.put("key", condition.getKey());
        input.put("path", condition.getValue());
        input.put("id_step_from", condition.getStepFrom());

        inputs.add(input);
      }
    }

    Map<String, Object> result = Map.of("input", inputs);
    return gson.toJson(result);
  }

  private String setInjectId(String injectId, String dataStep) {
    return setField(dataStep, "inject_id", injectId);
  }
}
