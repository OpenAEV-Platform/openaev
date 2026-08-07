import { CancelOutlined, PauseOutlined, PlayArrowOutlined } from '@mui/icons-material';
import { Button } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import {
  cancelAutonomousRun,
  pauseAutonomousRun,
  resumeAutonomousRun,
} from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousRun } from '../../../actions/autonomous/autonomous-types';
import { useFormatter } from '../../../components/i18n';

interface AutonomousRunControlsProps {
  run: AutonomousRun;
  onRunUpdate?: (run: AutonomousRun) => void;
}

/**
 * Compact lifecycle controls for an ACTIVE autonomous (AI-driven) run, styled as hero buttons.
 * Pause / resume / stop act on the run and its single underlying simulation through the autonomous
 * API - a manual scenario/simulation launch is never exposed. Rendered in the scenario hero only
 * while the run is active; once it settles the hero returns to the standard actions (AI builder to
 * rebuild, Normal / Autonomous to relaunch), so no settled-state controls live here anymore.
 */
const AutonomousRunControls: FunctionComponent<AutonomousRunControlsProps> = ({ run, onRunUpdate }) => {
  const { t } = useFormatter();
  const [busy, setBusy] = useState(false);
  const runId = run.autonomous_run_id;
  const status = run.autonomous_run_status;

  const withBusy = (action: Promise<{ data: AutonomousRun }>) => {
    setBusy(true);
    action.then(res => onRunUpdate?.(res.data)).catch(() => {}).finally(() => setBusy(false));
  };

  const isActive = status === 'RUNNING' || status === 'WAITING_INPUT';

  return (
    <>
      {status === 'RUNNING' && (
        <Button
          startIcon={<PauseOutlined />}
          variant="outlined"
          color="warning"
          size="small"
          disabled={busy}
          onClick={() => withBusy(pauseAutonomousRun(runId))}
        >
          {t('Pause')}
        </Button>
      )}
      {status === 'PAUSED' && (
        <Button
          startIcon={<PlayArrowOutlined />}
          variant="outlined"
          color="success"
          size="small"
          disabled={busy}
          onClick={() => withBusy(resumeAutonomousRun(runId))}
        >
          {t('Resume')}
        </Button>
      )}
      {(isActive || status === 'PAUSED' || status === 'PLANNING') && (
        <Button
          startIcon={<CancelOutlined />}
          variant="outlined"
          color="error"
          size="small"
          disabled={busy}
          onClick={() => withBusy(cancelAutonomousRun(runId))}
        >
          {t('Stop')}
        </Button>
      )}
    </>
  );
};

export default AutonomousRunControls;
