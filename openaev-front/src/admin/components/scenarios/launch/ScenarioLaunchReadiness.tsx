import { Button } from '@filigran/design-system';
import { ErrorOutlineOutlined, WarningAmberOutlined } from '@mui/icons-material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type Scenario } from '../../../../utils/api-types';
import loadScenarioLaunchBlockers, { failingInjectCount, hardBlockerCount } from './scenarioLaunchBlockers';

interface Props {
  scenario: Scenario;
  onOpen: () => void;
}

// The Hero body's control for launch blockers (design system Hero, "Scenario page"): it self-hides when
// the scenario can launch cleanly, and opens the launch check that lists each fix. Blockers keep the
// alert tone; injects that will fail (a revoked payload) only warn, since the launch still starts.
const ScenarioLaunchReadiness: FunctionComponent<Props> = ({ scenario, onOpen }) => {
  const { t } = useFormatter();
  const [counts, setCounts] = useState({
    hard: 0,
    failing: 0,
  });

  useEffect(() => {
    let stale = false;
    loadScenarioLaunchBlockers(scenario).then((blockers) => {
      if (!stale) {
        setCounts({
          hard: hardBlockerCount(blockers),
          failing: failingInjectCount(blockers),
        });
      }
    });
    return () => {
      stale = true;
    };
  }, [scenario]);

  if (counts.hard > 0) {
    return (
      <Button
        type="button"
        variant="destructive"
        priority="secondary"
        startIcon={<ErrorOutlineOutlined fontSize="small" />}
        onClick={onOpen}
        data-testid="scenario-launch-readiness"
      >
        {counts.hard === 1 ? t('1 to fix before launch') : t('{count} to fix before launch', { count: String(counts.hard) })}
      </Button>
    );
  }
  if (counts.failing > 0) {
    return (
      <Button
        type="button"
        priority="secondary"
        startIcon={<WarningAmberOutlined fontSize="small" />}
        onClick={onOpen}
        data-testid="scenario-launch-readiness"
      >
        {counts.failing === 1 ? t('1 inject will fail') : t('{count} injects will fail', { count: String(counts.failing) })}
      </Button>
    );
  }
  return null;
};

export default ScenarioLaunchReadiness;
