package io.openaev.api.chaining;

import com.google.gson.Gson;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.*;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.StepService;
import io.openaev.utils.TargetType;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class InjectExecutionStep implements ActionStep {
  private static final Gson gson = new Gson();
  InjectorContractService injectorContractService;
  UserService userService;
  AssetService assetService;
  TeamService teamService;
  TagService tagService;
  DocumentService documentService;
  InjectService injectService;
  TagRuleService tagRuleService;
  AssetGroupService assetGroupService;
  InjectorContractContentUtils injectorContractContentUtils;
  ExerciseService exerciseService;
  ConditionService conditionService;
  StepService stepService;

  @Override
  public void create(StepsCreateInput.StepCreateInput step, Workflow workflow) {
    String data = this.stepData(step, workflow.getSimulation());
    Condition condition = this.stepCondition(step, workflow);
    String input = this.stepInput(step.conditions);
    String outputParser = this.stepOutputParser(data);
    Step stepTemplate =
        Step.builder()
            .condition(condition)
            .data(data)
            .input(input)
            .output_parser(outputParser)
            .status(STEP_STATUS.TEMPLATE)
            .stepAction(STEP_ACTION_CLASS.INJECT_EXECUTION)
            .limitExecution(step.limitExecution)
            .workflow(workflow)
            .build();
    this.stepService.saveStep(stepTemplate);
  }

  @Override
  public void wait(StepsCreateInput.StepCreateInput stepTemplate, Workflow workflow, String input) {
    // CALL BY methode update() or by start simulation
    // Creation step WAIT add to Queue or Table Queue
  }

  @Override
  public void run(StepsCreateInput.StepCreateInput step, Workflow workflow) {
    // CALL BY QUEUE WAIT
    // Get params

    // Use input, complete inject ->

    // Save Inject
    // Execute Inject
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

    return gson.toJson(inject);
  }

  private String stepOutputParser(String data) {
    // inject.getPayload().get().getOutputParsers();
    // Nmap
    // Nuclei
    return "{}";
  }

  private Condition stepCondition(StepsCreateInput.StepCreateInput step, Workflow workflow) {
    ConditionCreateInput firstCondition =
        step.conditions.stream()
            .reduce(
                (a, b) -> {
                  throw new IllegalArgumentException("Only 1 condition can be first parent");
                })
            .orElseThrow(
                () -> new IllegalArgumentException("Only 1 condition can be first parent"));

    Condition first =
        Condition.builder()
            .type(firstCondition.getType())
            .key(firstCondition.getKey())
            .value(firstCondition.getValue())
            .build();
    first = conditionService.saveCondition(first);

    Map<String, Condition> temporaryIdAndSaveId = new HashMap<>();
    temporaryIdAndSaveId.put(firstCondition.getTemporaryId(), first);

    Map<String, List<ConditionCreateInput>> temporaryConditions = new HashMap<>();
    temporaryConditions =
        step.getConditions().stream()
            .collect(Collectors.groupingBy(ConditionCreateInput::getTemporaryIdConditionParent));

    Queue<String> currentId = new LinkedList<>();
    currentId.add(firstCondition.getTemporaryIdConditionParent());

    while (!currentId.isEmpty()) {
      String currentTemporaryId = currentId.poll();

      List<ConditionCreateInput> conditions = temporaryConditions.get(currentTemporaryId);

      for (ConditionCreateInput condition : conditions) {
        Condition current =
            Condition.builder()
                .type(condition.getType())
                .key(condition.getKey())
                .value(condition.getValue())
                .conditionParent(
                    temporaryIdAndSaveId.get(condition.getTemporaryIdConditionParent()))
                .build();

        current = conditionService.saveCondition(current);

        temporaryIdAndSaveId.put(condition.getTemporaryId(), current);

        currentId.add(condition.getTemporaryId());
      }
    }
    return first;
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
}
