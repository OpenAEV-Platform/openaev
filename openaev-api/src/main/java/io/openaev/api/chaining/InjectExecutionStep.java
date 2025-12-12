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
import io.openaev.utils.TargetType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class InjectExecutionStep implements ActionStep {
    Gson gson = new Gson();
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

    @Override
    public void create(StepsCreateInput.StepCreateInput step, Workflow workflow) {
        String data = this.stepData(step, workflow.getSimulationId());

    }

    @Override
    public void run(StepsCreateInput.StepCreateInput step, Workflow workflow) {

    }

    @Override
    public void end(StepsCreateInput.StepCreateInput step, Workflow workflow) {

    }
    private String stepData(StepsCreateInput.StepCreateInput step, Exercise exercise){

        InjectInput input = (InjectInput) step.inputStep;
        InjectorContract injectorContract =
                this.injectorContractService.injectorContract(input.getInjectorContract());
        Inject inject = input.toInject(injectorContract);
        inject.setUser(this.userService.currentUser());

        inject.setTeams(teamService.getTeamsByIds(input.getTeams()));
        inject.setAssets(assetService.assets(input.getAssets()));

        inject.setTags(tagService.tagSet(input.getTagIds()));

        List<InjectDocument> injectDocuments =
                input.getDocuments().stream()
                        .map(i -> i.toDocument(documentService.document(i.getDocumentId()), inject))
                        .toList();
        inject.setDocuments(injectDocuments);
        Set<Tag> tags = new HashSet<>();
        //TODO Scenario or EXERCISE
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
                            assetGroupService.assetGroups(input.getAssetGroups())));
        }

        // if inject content is null we add the defaults from the injector contract
        // this is the case when creating an inject from OpenCti
        if (inject.getContent() == null || inject.getContent().isEmpty()) {
            inject.setContent(
                    injectorContractContentUtils.getDynamicInjectorContractFieldsForInject(injectorContract));
        }

        return gson.toJson(inject);

    }
    private String stepCondition(StepsCreateInput.StepCreateInput step, Workflow workflow) {
        ConditionCreateInput firstCondition =
                step.conditions.stream()
                        .reduce( (a, b)-> {
                            throw new IllegalArgumentException("Only 1 condition can be first parent");
                        })
                        .orElseThrow( ()->  new IllegalArgumentException("Only 1 condition can be first parent") );

        Condition first = Condition.builder()
                .type(firstCondition.getType())
                .key(firstCondition.getKey())
                .value(firstCondition.getValue()).build();
        first = conditionService.saveCondition(first);

        Map<String,Condition> temporaryIdAndSaveId = new HashMap<>();
        temporaryIdAndSaveId.put(firstCondition.getTemporaryId(), first);

        Map<String, List<ConditionCreateInput>> temporaryConditions = new HashMap<>();
        temporaryConditions = step.getConditions().stream().collect(Collectors.groupingBy(ConditionCreateInput::getTemporaryIdConditionParent));

        Queue<String> currentId = new LinkedList<>();
        currentId.add(firstCondition.getTemporaryIdConditionParent());

        while (!currentId.isEmpty()) {
            String currentTemporaryId = currentId.poll();

            List<ConditionCreateInput> conditions = temporaryConditions.get(currentTemporaryId);

            for (ConditionCreateInput condition : conditions) {
                Condition current = Condition.builder()
                        .type(condition.getType())
                        .key(condition.getKey())
                        .value(condition.getValue())
                        .conditionParent(temporaryIdAndSaveId.get(condition.getTemporaryIdConditionParent()))
                        .build();

                current = conditionService.saveCondition(current);

                temporaryIdAndSaveId.put(condition.getTemporaryId(), current);

                currentId.add(condition.getTemporaryId());
            }
        }
        return first.getId();
    }
}
