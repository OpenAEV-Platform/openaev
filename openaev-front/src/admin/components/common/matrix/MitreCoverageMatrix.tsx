import { type FunctionComponent, useCallback, useContext, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router';

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
import { CustomDashboardContext, type WidgetResultsConf } from '../../workspaces/custom_dashboards/CustomDashboardContext';
import { CONTEXTUAL_MITRE_WIDGET_ID, contextualResultsUrl, type ContextualSource } from '../../workspaces/custom_dashboards/results/contextualWidgets';
import SecurityCoverageContent from '../../workspaces/custom_dashboards/widgets/viz/SecurityCoverageContent';

interface Props {
  // Stable id used to persist the kill-chain / coverage-scope selections in
  // localStorage (e.g. `scenario-mitre-<id>` / `simulation-mitre-<id>`).
  widgetId: string;
  injectResults: InjectExpectationResultsByAttackPattern[] | null | undefined;
  /**
   * When set, technique boxes become clickable and drill down to the full-page
   * results explorer, scoped to this simulation / scenario (same actionability
   * as the dashboards). Leave unset for sample/preview data where a drill-down
   * would land on an empty list.
   */
  resultsContext?: {
    source: ContextualSource;
    contextId: string;
  };
  /**
   * Entity-configured default kill chain (scenario / simulation setting): the
   * matrix lands on it when the user has no remembered selection. The user's
   * own selection, persisted in local storage, still overrides it.
   */
  defaultKillChain?: string | null;
}

// The home dashboard's ATT&CK coverage matrix (SecurityCoverageContent) is fed
// two ES series - successes then failures - keyed by attack-pattern id. Reshape
// the per-attack-pattern expectation results the scenario/simulation overviews
// already fetch into that shape so both surfaces share the home widget's
// rendering (kill-chain selector, heat cells) in covered-only result mode.
const MitreCoverageMatrix: FunctionComponent<Props> = ({ widgetId, injectResults, resultsContext, defaultKillChain }) => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
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

  // The widget only reads `field` (the technique-click filter key): use the ES
  // attack-patterns field so the drill-down scope matches the runtime endpoint.
  const widgetConfig = useMemo(
    () => ({ field: 'base_attack_patterns_side' } as unknown as StructuralHistogramWidget),
    [],
  );

  // Technique clicks bubble up through the dashboard context (the matrix body
  // is shared with the dashboard widget): the overviews are not inside a
  // dashboard, so provide a scoped context whose drill-down navigates to the
  // results explorer with a synthetic contextual widget.
  const parentContext = useContext(CustomDashboardContext);
  const openWidgetResults = useCallback((conf: WidgetResultsConf) => {
    if (!resultsContext) {
      return;
    }
    navigate(contextualResultsUrl(
      CONTEXTUAL_MITRE_WIDGET_ID,
      resultsContext.source,
      resultsContext.contextId,
      `${location.pathname}${location.search}`,
      conf.filter_values_map,
    ));
  }, [navigate, location, resultsContext]);
  const contextValue = useMemo(() => ({
    ...parentContext,
    openWidgetResults,
  }), [parentContext, openWidgetResults]);

  // Overviews are result views: show only techniques actually covered by the
  // scenario / simulation, without the coverage-planning controls and KPI.
  const content = <SecurityCoverageContent widgetId={widgetId} widgetConfig={widgetConfig} data={data} coveredOnly preferredKillChain={defaultKillChain} />;
  if (!resultsContext) {
    return content;
  }
  return (
    <CustomDashboardContext.Provider value={contextValue}>
      {content}
    </CustomDashboardContext.Provider>
  );
};

export default MitreCoverageMatrix;
