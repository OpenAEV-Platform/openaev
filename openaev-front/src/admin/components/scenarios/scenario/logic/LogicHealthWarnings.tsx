import { Alert, AlertTitle } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import type { Workflow } from '../../../../../utils/api-types-custom';
import { getActionsProvisioningField, getLinkedSteps, getRootSteps } from './logicUtils';

interface Props {
  workflow: Workflow;
}

const LogicHealthWarnings: FunctionComponent<Props> = ({ workflow }) => {
  const { t } = useFormatter();
  const rootSteps = getRootSteps(workflow);
  const linkedSteps = getLinkedSteps(workflow);
  const warnings: string[] = [];

  if (workflow.workflow_steps.length === 0) {
    warnings.push(t('No actions defined. Add at least one action to build your chaining scenario.'));
  }

  if (rootSteps.length === 0 && workflow.workflow_steps.length > 0) {
    warnings.push(t('No root actions found. At least one action should have no triggering conditions.'));
  }

  // Check if events reference fields not provisioned by any action
  for (const step of linkedSteps) {
    for (const condition of step.step_conditions) {
      if (condition.condition_key) {
        const providers = getActionsProvisioningField(rootSteps, condition.condition_key);
        if (providers.length === 0) {
          warnings.push(
            t('Event condition references field "{{field}}" which is not provisioned by any action.', { field: condition.condition_key }),
          );
        }
      }
    }
  }

  if (warnings.length === 0) return null;

  return (
    <>
      {warnings.map((warning, index) => (
        <Alert key={index} severity="warning" sx={{ mb: 2 }}>
          <AlertTitle>{t('Warning')}</AlertTitle>
          {warning}
        </Alert>
      ))}
    </>
  );
};

export default LogicHealthWarnings;
