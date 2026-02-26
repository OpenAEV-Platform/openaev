package io.openaev.rest.workflow.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowOutputDto {

  @JsonProperty("workflow_id")
  private String workflowId;

  @JsonProperty("workflow_status")
  private WorkflowStatus status;

  @JsonProperty("workflow_version")
  private int version;

  @JsonProperty("workflow_is_edited")
  private boolean isEdited;

  @JsonProperty("workflow_created_at")
  private Instant createdAt;

  @JsonProperty("workflow_updated_at")
  private Instant updatedAt;

  @JsonProperty("workflow_steps")
  private List<StepOutputDto> steps;

  @Data
  @Builder
  public static class StepOutputDto {

    @JsonProperty("step_id")
    private String stepId;

    @JsonProperty("step_action_class")
    private StepActionClass stepAction;

    @JsonProperty("step_data")
    private String data;

    @JsonProperty("step_output_parser")
    private String outputParser;

    @JsonProperty("step_limit_execution")
    private int limitExecution;

    @JsonProperty("step_field_scope")
    private StepFieldScope fieldScope;

    @JsonProperty("step_status")
    private StepStatus status;

    @JsonProperty("step_created_at")
    private Instant createdAt;

    @JsonProperty("step_updated_at")
    private Instant updatedAt;

    @JsonProperty("step_conditions")
    private List<ConditionOutputDto> conditions;
  }

  @Data
  @Builder
  public static class ConditionOutputDto {

    @JsonProperty("condition_id")
    private String conditionId;

    @JsonProperty("condition_key")
    private String key;

    @JsonProperty("condition_value")
    private String value;

    @JsonProperty("condition_type")
    private ConditionType type;

    @JsonProperty("step_from_id")
    private String stepFromId;

    @JsonProperty("condition_parent_id")
    private String conditionParentId;

    @JsonProperty("condition_created_at")
    private Instant createdAt;

    @JsonProperty("condition_updated_at")
    private Instant updatedAt;
  }

  public static WorkflowOutputDto from(
      Workflow workflow, List<Step> steps, List<Condition> conditions) {
    List<StepOutputDto> stepDtos =
        steps.stream()
            .map(
                step -> {
                  List<ConditionOutputDto> condDtos =
                      conditions.stream()
                          .filter(c -> c.getStep().getId().equals(step.getId()))
                          .map(
                              c ->
                                  ConditionOutputDto.builder()
                                      .conditionId(c.getId())
                                      .key(c.getKey())
                                      .value(c.getValue())
                                      .type(c.getType())
                                      .stepFromId(
                                          c.getStepFrom() != null
                                              ? c.getStepFrom().getId()
                                              : null)
                                      .conditionParentId(
                                          c.getConditionParent() != null
                                              ? c.getConditionParent().getId()
                                              : null)
                                      .createdAt(c.getCreationDate())
                                      .updatedAt(c.getUpdateDate())
                                      .build())
                          .toList();
                  return StepOutputDto.builder()
                      .stepId(step.getId())
                      .stepAction(step.getStepAction())
                      .data(step.getData())
                      .outputParser(step.getOutput_parser())
                      .limitExecution(step.getLimitExecution())
                      .fieldScope(step.getFieldScope())
                      .status(step.getStatus())
                      .createdAt(step.getCreatedAt())
                      .updatedAt(step.getUpdatedAt())
                      .conditions(condDtos)
                      .build();
                })
            .toList();
    return WorkflowOutputDto.builder()
        .workflowId(workflow.getId())
        .status(workflow.getStatus())
        .version(workflow.getVersion())
        .isEdited(workflow.isEdited())
        .createdAt(workflow.getWorkflowCreatedAt())
        .updatedAt(workflow.getWorkflowUpdatedAt())
        .steps(stepDtos)
        .build();
  }
}
