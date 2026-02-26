import type { Workflow, WorkflowStep } from '../../../../../utils/api-types-custom';

/**
 * Extract output types from a step's output_parser JSON.
 * The output parser structure: { output_parsers: [{ contract_output_elements: [{ type: "port" }] }] }
 */
export const extractOutputTypesFromStepData = (step: WorkflowStep): string[] => {
  if (!step.step_output_parser) return [];
  try {
    const parser = JSON.parse(step.step_output_parser);
    if (Array.isArray(parser)) {
      return parser.flatMap(
        (p: { contract_output_elements?: { contract_output_element_type?: string }[] }) =>
          (p.contract_output_elements ?? [])
            .map(el => el.contract_output_element_type)
            .filter((t): t is string => !!t),
      );
    }
    return [];
  } catch {
    return [];
  }
};

/**
 * Get root steps (steps that have no conditions pointing to them, i.e. no dependencies)
 */
export const getRootSteps = (workflow: Workflow): WorkflowStep[] => {
  return workflow.workflow_steps.filter(
    step => step.step_conditions.length === 0,
  );
};

/**
 * Get linked steps (steps that have conditions, i.e. events/dependent steps)
 */
export const getLinkedSteps = (workflow: Workflow): WorkflowStep[] => {
  return workflow.workflow_steps.filter(
    step => step.step_conditions.length > 0,
  );
};

/**
 * Find all actions (steps) that provision a given output field type
 */
export const getActionsProvisioningField = (
  steps: WorkflowStep[],
  fieldType: string,
): WorkflowStep[] => {
  return steps.filter(step => {
    const outputTypes = extractOutputTypesFromStepData(step);
    return outputTypes.includes(fieldType);
  });
};

/**
 * Parse step data to get the inject contract label (if available)
 */
export const getStepLabel = (step: WorkflowStep): string => {
  if (!step.step_data) return `Step ${step.step_id.substring(0, 8)}`;
  try {
    const data = JSON.parse(step.step_data);
    return data.inject_title
      || data.injector_contract_label
      || data.label
      || `Step ${step.step_id.substring(0, 8)}`;
  } catch {
    return `Step ${step.step_id.substring(0, 8)}`;
  }
};
