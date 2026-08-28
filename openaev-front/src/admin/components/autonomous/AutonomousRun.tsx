import { Alert } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router';

import { fetchAutonomousRun } from '../../../actions/autonomous/autonomous-actions';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { SCENARIO_BASE_URL, SIMULATION_BASE_URL } from '../../../constants/BaseUrls';

/**
 * Legacy entry point for a single autonomous run. The autonomous cockpit now lives on the run's
 * scenario detail page (AI overview + gated tabs + always-open reasoning panel + run controls),
 * which is reachable from the Scenarios list. This component keeps old {@code /admin/autonomous/:runId}
 * bookmarks working by resolving the run and redirecting to its scenario (falling back to the
 * simulation, which offers the same experience).
 */
const AutonomousRun: FunctionComponent = () => {
  const { runId } = useParams<{ runId: string }>();
  const { t } = useFormatter();
  const [target, setTarget] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    if (!runId) {
      setNotFound(true);
      return;
    }
    fetchAutonomousRun(runId)
      .then((res) => {
        const scenarioId = res.data.autonomous_run_scenario_id;
        const simulationId = res.data.autonomous_run_simulation_id;
        if (scenarioId) {
          setTarget(`${SCENARIO_BASE_URL}/${scenarioId}`);
        } else if (simulationId) {
          setTarget(`${SIMULATION_BASE_URL}/${simulationId}`);
        } else {
          setNotFound(true);
        }
      })
      .catch(() => setNotFound(true));
  }, [runId]);

  if (notFound) {
    return <Alert severity="error">{t('Autonomous run not found')}</Alert>;
  }
  if (target) {
    return <Navigate to={target} replace />;
  }
  return <Loader />;
};

export default AutonomousRun;
