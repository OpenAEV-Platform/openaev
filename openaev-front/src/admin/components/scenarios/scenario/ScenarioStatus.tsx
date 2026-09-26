import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type Scenario } from '../../../../utils/api-types';

interface Props {
  scenario?: Scenario;
  // Direct scheduled flag for callers that only have the indexed status
  // (e.g. ES-backed dashboard list widgets), not the full scenario.
  scheduled?: boolean;
  variant?: 'list';
}

export const SCENARIO_SCHEDULED_STATUS = 'Scheduled';
export const SCENARIO_NOT_SCHEDULED_STATUS = 'Not planned';

const scenarioStatus: FunctionComponent<Props> = ({
  scenario,
  scheduled,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  if (scheduled ?? scenario?.scenario_recurrence) {
    return (
      <Chip label={t(SCENARIO_SCHEDULED_STATUS)} severity="low" />
    );
  }
  return (
    <Chip label={t(SCENARIO_NOT_SCHEDULED_STATUS)} severity="neutral" />
  );
};
export default scenarioStatus;
