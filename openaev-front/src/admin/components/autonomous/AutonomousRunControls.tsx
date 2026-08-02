import { CancelOutlined, PauseOutlined, PlayArrowOutlined, RestartAltOutlined, RocketLaunchOutlined } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import {
  cancelAutonomousRun,
  pauseAutonomousRun,
  promoteAutonomousRun,
  restartAutonomousRun,
  resumeAutonomousRun,
  startAutonomousRun,
} from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousRun } from '../../../actions/autonomous/autonomous-types';
import { fetchScenario } from '../../../actions/scenarios/scenario-actions';
import { useFormatter } from '../../../components/i18n';
import { useAppDispatch } from '../../../utils/hooks';

interface AutonomousRunControlsProps {
  run: AutonomousRun;
  onRunUpdate?: (run: AutonomousRun) => void;
}

/**
 * Compact lifecycle controls for an autonomous (AI-driven) run, styled as hero buttons. Pause /
 * resume / stop act on the run and its single underlying simulation through the autonomous API - a
 * manual scenario/simulation launch is never exposed. Used in the scenario hero so an autonomous
 * run can be paused or stopped without leaving the scenario, mirroring the reasoning panel. A
 * terminal run (completed / failed / canceled) is one-shot and cannot resume in place, so it offers
 * a Restart that re-runs it against the SAME scenario (a fresh simulation replaces the old one),
 * fully resetting the cockpit without ever spawning a new scenario.
 */
const AutonomousRunControls: FunctionComponent<AutonomousRunControlsProps> = ({ run, onRunUpdate }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [busy, setBusy] = useState(false);
  const [promoteOpen, setPromoteOpen] = useState(false);
  const runId = run.autonomous_run_id;
  const status = run.autonomous_run_status;
  const planMode = run.autonomous_run_plan_mode === true;

  const withBusy = (action: Promise<{ data: AutonomousRun }>) => {
    setBusy(true);
    action.then(res => onRunUpdate?.(res.data)).catch(() => {}).finally(() => setBusy(false));
  };

  const isActive = status === 'RUNNING' || status === 'WAITING_INPUT';
  const isTerminal = status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELED';
  // A settled dry-run can be promoted to a live run: PLANNED is the happy path, but a plan that
  // stopped on FAILED/CANCELED can still be run for real.
  const canPromote = planMode && (status === 'PLANNED' || status === 'FAILED' || status === 'CANCELED');

  // Run for real: promote the plan in place (fresh executing simulation, plan kept as guidance),
  // then engage the orchestrator on the live run and refresh the scenario like Restart does.
  const handlePromote = () => {
    setPromoteOpen(false);
    setBusy(true);
    promoteAutonomousRun(runId)
      .then(() => startAutonomousRun(runId))
      .then((res) => {
        onRunUpdate?.(res.data);
        if (res.data.autonomous_run_scenario_id) {
          dispatch(fetchScenario(res.data.autonomous_run_scenario_id));
        }
      })
      .catch(() => {})
      .finally(() => setBusy(false));
  };

  // Restart re-runs the SAME scenario in place: the backend tears the old simulation + timeline
  // down, provisions a fresh simulation, and resets the run to CREATED; we then start it again. No
  // new scenario is created, so we stay on the current page - pushing the fresh run up (new
  // simulation id, RUNNING) resets the overview + reasoning panel, and reloading the scenario
  // refreshes its exercise list so the attack-path tab drops the torn-down run and picks up the new one.
  const handleRestart = () => {
    setBusy(true);
    restartAutonomousRun(runId)
      .then(() => startAutonomousRun(runId))
      .then((res) => {
        onRunUpdate?.(res.data);
        if (res.data.autonomous_run_scenario_id) {
          dispatch(fetchScenario(res.data.autonomous_run_scenario_id));
        }
      })
      .catch(() => {})
      .finally(() => setBusy(false));
  };

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
      {canPromote && (
        <Button
          startIcon={<RocketLaunchOutlined />}
          variant="contained"
          color="primary"
          size="small"
          disabled={busy}
          onClick={() => setPromoteOpen(true)}
          data-testid="button-autonomous-run-for-real"
        >
          {t('Run for real')}
        </Button>
      )}
      {isTerminal && (
        <Button
          startIcon={<RestartAltOutlined />}
          variant="outlined"
          color="primary"
          size="small"
          disabled={busy}
          onClick={handleRestart}
        >
          {t('Restart')}
        </Button>
      )}
      <Dialog open={promoteOpen} onClose={() => setPromoteOpen(false)} maxWidth="xs">
        <DialogTitle>{t('Run this plan for real?')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('This starts a fresh live run. The AI will follow the plan as closely as possible but will adapt in real time to what it finds. The planned attack path is cleared and rebuilt live.')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPromoteOpen(false)} disabled={busy}>
            {t('Cancel')}
          </Button>
          <Button
            onClick={handlePromote}
            variant="contained"
            color="primary"
            disabled={busy}
            startIcon={<RocketLaunchOutlined />}
          >
            {t('Run for real')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default AutonomousRunControls;
