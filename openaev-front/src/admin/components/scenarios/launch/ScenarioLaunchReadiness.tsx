import { Button } from '@filigran/design-system';
import { ErrorOutlineOutlined } from '@mui/icons-material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type Scenario } from '../../../../utils/api-types';
import loadScenarioLaunchBlockers, { hardBlockerCount } from './scenarioLaunchBlockers';

interface Props {
  scenario: Scenario;
  onOpen: () => void;
}

// The Hero body's alert-toned control for launch blockers (design system Hero, "Scenario page"):
// it self-hides when the scenario can launch, and opens the launch check that lists each fix.
const ScenarioLaunchReadiness: FunctionComponent<Props> = ({ scenario, onOpen }) => {
  const { t } = useFormatter();
  const [count, setCount] = useState(0);

  useEffect(() => {
    let stale = false;
    loadScenarioLaunchBlockers(scenario).then((blockers) => {
      if (!stale) setCount(hardBlockerCount(blockers));
    });
    return () => {
      stale = true;
    };
  }, [scenario]);

  if (count === 0) {
    return null;
  }
  return (
    <Button
      type="button"
      variant="destructive"
      priority="secondary"
      startIcon={<ErrorOutlineOutlined fontSize="small" />}
      onClick={onOpen}
      data-testid="scenario-launch-readiness"
    >
      {count === 1 ? t('1 to fix before launch') : t('{count} to fix before launch', { count: String(count) })}
    </Button>
  );
};

export default ScenarioLaunchReadiness;
