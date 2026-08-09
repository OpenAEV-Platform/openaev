import { Stack } from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { type AutonomousRun, type AutonomousRunStatus } from '../../../actions/autonomous/autonomous-types';
import { fetchExerciseExpectationResult, fetchExerciseInjectExpectationResults } from '../../../actions/exercises/exercise-action';
import { SectionBlock } from '../../../components/common/detail/EntityDetailCommon';
import PostureGauges from '../../../components/common/detail/PostureGauges';
import SAMPLE_POSTURE from '../../../components/common/detail/samplePosture';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { type ExpectationResultsByType, type InjectExpectationResultsByAttackPattern } from '../../../utils/api-types';
import MitreCoverageMatrix from '../common/matrix/MitreCoverageMatrix';
import { CONTEXTUAL_POSTURE_WIDGET_ID, contextualResultsUrl } from '../workspaces/custom_dashboards/results/contextualWidgets';
import SamplePreview from '../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import AutonomousOutcome from './AutonomousOutcome';

const ACTIVE_STATUSES: AutonomousRunStatus[] = ['PLANNING', 'RUNNING', 'WAITING_INPUT'];
const POLL_INTERVAL_MS = 5000;

interface AutonomousOverviewProps { run: AutonomousRun }

/**
 * Overview tab of an autonomous (AI-driven) run. It renders the durable, exportable
 * {@link AutonomousOutcome} (mission, decision timeline, capability gaps + proof-of-exploitation)
 * followed by the run posture gauges and MITRE kill-chain coverage of its single simulation. The
 * live reasoning stream lives in the always-open right panel, so this tab is the readable read of
 * the run's outcome.
 *
 * Plan mode is non-executing (no injects run), so the posture / kill-chain sections are dropped:
 * there are no results to show and the outcome's proofs column is dropped for the same reason.
 */
const AutonomousOverview: FunctionComponent<AutonomousOverviewProps> = ({ run }) => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const location = useLocation();
  const status = run.autonomous_run_status;
  const simulationId = run.autonomous_run_simulation_id;
  const isPlanMode = run.autonomous_run_plan_mode;

  // An autonomous run owns exactly one simulation, so its posture and MITRE coverage are simply that
  // simulation's expectation results - the same widgets the manual overview surfaces, minus the
  // multi-run trend (a one-shot has no history to trend).
  const [postureResults, setPostureResults] = useState<ExpectationResultsByType[] | null>(null);
  const [injectResults, setInjectResults] = useState<InjectExpectationResultsByAttackPattern[] | null>(null);

  const reloadResults = useCallback(() => {
    if (!simulationId) {
      return Promise.resolve();
    }
    return Promise.all([
      fetchExerciseExpectationResult(simulationId)
        .then((res: { data: ExpectationResultsByType[] }) => setPostureResults(res.data))
        .catch(() => {}),
      fetchExerciseInjectExpectationResults(simulationId)
        .then((res: { data: InjectExpectationResultsByAttackPattern[] }) => setInjectResults(res.data))
        .catch(() => {}),
    ]);
  }, [simulationId]);

  useEffect(() => {
    reloadResults();
  }, [reloadResults]);

  const isActive = ACTIVE_STATUSES.includes(status);
  useEffect(() => {
    if (isPlanMode || !isActive) {
      return undefined;
    }
    const interval = setInterval(() => {
      reloadResults();
    }, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [isPlanMode, isActive, reloadResults]);

  const attackPatternIds = injectResults
    ? R.uniq(
        injectResults
          .filter(injectResult => !!injectResult.inject_attack_pattern)
          .flatMap(injectResult => injectResult.inject_attack_pattern) as unknown as string[],
      )
    : [];
  const hasMitreResults = !!injectResults && attackPatternIds.length > 0;

  // Posture gauge clicks drill down to the expectations behind the ring, scoped to the run's single
  // simulation - same actionability as the manual simulation overview.
  const openPostureResults = useCallback((type: string) => {
    if (!simulationId) {
      return;
    }
    navigate(contextualResultsUrl(
      CONTEXTUAL_POSTURE_WIDGET_ID,
      'simulation',
      simulationId,
      `${location.pathname}${location.search}`,
      { inject_expectation_type: [type] },
    ));
  }, [navigate, location, simulationId]);

  return (
    <Stack sx={{ gap: 2 }}>
      <AutonomousOutcome run={run} />

      {/* Run posture: the single simulation's prevention / detection / vulnerability / human-response
          gauges. A plan never executes, so it produces no results - the section is dropped in plan
          mode rather than showing a permanent sample. Never a blank box otherwise - falls back to an
          illustrative greyed sample while the run has not produced results yet. */}
      {!isPlanMode && simulationId && (
        <SectionBlock title={t('Run posture')}>
          {(() => {
            if (!postureResults) {
              return <Loader variant="inElement" />;
            }
            if (postureResults.length === 0) {
              return (
                <SamplePreview active variant="subtle">
                  <PostureGauges expectationResultsByTypes={SAMPLE_POSTURE} />
                </SamplePreview>
              );
            }
            return (
              <PostureGauges
                expectationResultsByTypes={postureResults}
                humanValidationLink={`/admin/simulations/${simulationId}/execution/validations`}
                onTypeClick={openPostureResults}
              />
            );
          })()}
        </SectionBlock>
      )}

      {/* Kill chain results: the MITRE ATT&CK coverage of the run's simulation. Only shown once the
          run has produced technique-level results (a one-shot has no planned matrix to preview), and
          never in plan mode. */}
      {!isPlanMode && simulationId && hasMitreResults && (
        <SectionBlock title={t('Kill chain results')}>
          <MitreCoverageMatrix
            widgetId={`autonomous-mitre-${simulationId}`}
            injectResults={injectResults}
            resultsContext={{
              source: 'simulation',
              contextId: simulationId,
            }}
          />
        </SectionBlock>
      )}
    </Stack>
  );
};

export default AutonomousOverview;
