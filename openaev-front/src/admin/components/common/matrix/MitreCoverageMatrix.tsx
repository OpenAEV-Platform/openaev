import { type FunctionComponent, useMemo } from 'react';

import { fetchAttackPatterns } from '../../../../actions/AttackPattern';
import { fetchKillChainPhases } from '../../../../actions/KillChainPhase';
import {
  type EsSeries,
  type EsSeriesData,
  type InjectExpectationResultsByAttackPattern,
  type StructuralHistogramWidget,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import SecurityCoverageContent from '../../workspaces/custom_dashboards/widgets/viz/SecurityCoverageContent';

interface Props {
  // Stable id used to persist the kill-chain / coverage-scope selections in
  // localStorage (e.g. `scenario-mitre-<id>` / `simulation-mitre-<id>`).
  widgetId: string;
  injectResults: InjectExpectationResultsByAttackPattern[] | null | undefined;
}

// The home dashboard's ATT&CK coverage matrix (SecurityCoverageContent) is fed
// two ES series - successes then failures - keyed by attack-pattern id. Reshape
// the per-attack-pattern expectation results the scenario/simulation overviews
// already fetch into that shape so both surfaces share the home widget's
// rendering (kill-chain selector, heat cells) in covered-only result mode.
const MitreCoverageMatrix: FunctionComponent<Props> = ({ widgetId, injectResults }) => {
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
    dispatch(fetchKillChainPhases());
  });

  const data: EsSeries[] = useMemo(() => {
    const success: EsSeriesData[] = [];
    const failure: EsSeriesData[] = [];
    (injectResults ?? []).forEach((entry) => {
      const attackPatternId = entry.inject_attack_pattern;
      if (!attackPatternId) return;
      let successCount = 0;
      let failureCount = 0;
      (entry.inject_expectation_results ?? []).forEach(inject =>
        (inject.results ?? []).forEach(result =>
          (result.distribution ?? []).forEach((slice) => {
            if (slice.id === 'SUCCESS') successCount += slice.value ?? 0;
            else if (slice.id === 'FAILED' || slice.id === 'PARTIAL') failureCount += slice.value ?? 0;
          }),
        ),
      );
      success.push({
        key: attackPatternId,
        value: successCount,
      });
      failure.push({
        key: attackPatternId,
        value: failureCount,
      });
    });
    return [
      {
        label: 'success',
        data: success,
      },
      {
        label: 'failure',
        data: failure,
      },
    ];
  }, [injectResults]);

  // The widget only reads `field` (for its click-through filter); the standalone
  // usage keeps the default no-op drill-down, so a minimal config is enough.
  const widgetConfig = useMemo(
    () => ({ field: 'inject_attack_pattern' } as unknown as StructuralHistogramWidget),
    [],
  );

  // Overviews are result views: show only techniques actually covered by the
  // scenario / simulation, without the coverage-planning controls and KPI.
  return <SecurityCoverageContent widgetId={widgetId} widgetConfig={widgetConfig} data={data} coveredOnly />;
};

export default MitreCoverageMatrix;
