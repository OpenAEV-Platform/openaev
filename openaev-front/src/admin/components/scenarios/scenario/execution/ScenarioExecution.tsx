import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { fetchSimulationsMetaById } from '../../../../../actions/attack-path/attack-path-actions';
import { fetchExercise } from '../../../../../actions/Exercise';
import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type Scenario } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import ExecutionOverview from '../../../simulations/simulation/timeline/ExecutionOverview';

// Scenario-context Execution tab. Like the Attack path tab, it reflects the scenario's most recent
// simulation - the live execution overview (hero, attack timeline, execution board), refreshed in
// real time. The simulation right-hand execution menu is dropped: this is a read-only mirror of the
// latest run, not the full simulation execution area. Available for every scenario type (time-based,
// chained, autonomous). The scenario is already loaded by the parent Index route.
const ScenarioExecution: FunctionComponent = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  // Stable reference so the resolution effect does not refetch on every render.
  const exerciseIds = useMemo(() => scenario?.scenario_exercises ?? [], [scenario?.scenario_exercises]);

  const [latestSimulationId, setLatestSimulationId] = useState<string | null>(null);
  const [resolving, setResolving] = useState<boolean>(true);

  // Resolve the most recent simulation of this scenario by start date - the same way the Attack path
  // tab seeds its default run - then hand it to the shared Execution overview.
  useEffect(() => {
    let cancelled = false;
    if (exerciseIds.length === 0) {
      setLatestSimulationId(null);
      setResolving(false);
      return () => {
        cancelled = true;
      };
    }
    setResolving(true);
    fetchSimulationsMetaById(exerciseIds)
      .then(({ data }) => {
        if (cancelled) return;
        const mostRecent = [...(data ?? [])].sort((a, b) =>
          (b.exercise_start_date ?? '').localeCompare(a.exercise_start_date ?? ''))[0];
        setLatestSimulationId(mostRecent?.exercise_id ?? exerciseIds[0]);
      })
      .catch(() => {
        // The metadata read is display-only; fall back to the first id so the overview still loads.
        if (!cancelled) setLatestSimulationId(exerciseIds[0]);
      })
      .finally(() => {
        if (!cancelled) setResolving(false);
      });
    return () => {
      cancelled = true;
    };
  }, [exerciseIds]);

  // The Execution overview reads the simulation from the store but does not fetch it itself (the
  // simulation Index normally does), so load it here - through useDataLoader so it also refreshes on
  // every stream reconnect, matching the simulation cockpit.
  useDataLoader(() => {
    if (latestSimulationId) {
      dispatch(fetchExercise(latestSimulationId));
    }
  }, [latestSimulationId]);

  if (resolving) {
    return <Loader />;
  }
  if (!latestSimulationId) {
    return <Empty message={t('No simulation has run for this scenario yet.')} />;
  }
  return (
    <ExecutionOverview exerciseId={latestSimulationId} showMenu={false} />
  );
};

export default ScenarioExecution;
