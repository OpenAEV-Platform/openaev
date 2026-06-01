import type { WorkflowConfigurationOutput } from '../../utils/api-types';

export interface WorkflowConfigurationHelper { getWorkflowConfiguration: (workflowId: string) => WorkflowConfigurationOutput }
