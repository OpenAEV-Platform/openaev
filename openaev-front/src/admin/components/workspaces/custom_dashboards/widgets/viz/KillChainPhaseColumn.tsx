import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useContext, useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import type { AttackPatternHelper } from '../../../../../../actions/attack_patterns/attackpattern-helper';
import type { KillChainPhaseHelper } from '../../../../../../actions/kill_chain_phases/killchainphase-helper';
import { useHelper } from '../../../../../../store';
import type { AttackPattern, KillChainPhase, StructuralHistogramWidget } from '../../../../../../utils/api-types';
import { sortAttackPattern } from '../../../../../../utils/attack_patterns/attack_patterns';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import AttackPatternBox from './AttackPatternBox';
import { type CoverageFilter } from './SecurityCoverageContent';
import { type ResolvedTTPData } from './securityCoverageUtils';

const useStyles = makeStyles()(theme => ({
  column: {
    display: 'grid',
    gap: theme.spacing(1),
    paddingBottom: theme.spacing(1),
    width: '170px',
  },
}));

// Build index by external_id for O(1) lookups
const buildExternalIdIndex = (data: ResolvedTTPData[]): Map<string, ResolvedTTPData[]> => {
  const index = new Map<string, ResolvedTTPData[]>();
  for (const item of data) {
    if (item.attack_pattern_external_id) {
      const existing = index.get(item.attack_pattern_external_id) ?? [];
      existing.push(item);
      index.set(item.attack_pattern_external_id, existing);
    }
  }
  return index;
};

interface AttackPatternStats {
  attackPattern: AttackPattern;
  success: number;
  failure: number;
  total: number;
  /**
   * The technique appears in the series at all (exercised by at least one
   * inject), even if no expectation result has been scored yet. Drives the
   * covered/gaps scopes so a not-yet-run simulation still lists its techniques
   * (rendered muted by AttackPatternBox until results flow in).
   */
  present: boolean;
}

const KillChainPhaseColumn: FunctionComponent<{
  widgetId: string;
  widgetConfig: StructuralHistogramWidget;
  killChainPhase: KillChainPhase;
  coverageFilter: CoverageFilter;
  resolvedDataSuccess: ResolvedTTPData[];
  resolvedDataFailure: ResolvedTTPData[];
}> = ({ widgetId, widgetConfig, killChainPhase, coverageFilter, resolvedDataSuccess, resolvedDataFailure }) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { openWidgetDataDrawer } = useContext(CustomDashboardContext);

  // Fetching data - stable selector
  const { attackPatternMap }: { attackPatternMap: Record<string, AttackPattern> } = useHelper(
    (helper: AttackPatternHelper & KillChainPhaseHelper) => ({ attackPatternMap: helper.getAttackPatternsMap() }),
  );

  // Memoize attack patterns for this kill chain phase
  const attackPatterns = useMemo(() => {
    return Object.values(attackPatternMap)
      .filter((attackPattern: AttackPattern) =>
        attackPattern.attack_pattern_kill_chain_phases?.includes(killChainPhase.phase_id)
        && attackPattern.attack_pattern_parent === null, // Remove sub techniques
      )
      .toSorted(sortAttackPattern);
  }, [attackPatternMap, killChainPhase.phase_id]);

  // Build indexes for O(1) lookups instead of O(n) filtering per attack pattern
  const successIndex = useMemo(
    () => buildExternalIdIndex(resolvedDataSuccess),
    [resolvedDataSuccess],
  );

  const failureIndex = useMemo(
    () => buildExternalIdIndex(resolvedDataFailure),
    [resolvedDataFailure],
  );

  // Pre-compute all attack pattern stats
  const attackPatternStats = useMemo((): AttackPatternStats[] => {
    return attackPatterns.map((attackPattern) => {
      const externalId = attackPattern.attack_pattern_external_id;
      const successData = externalId ? (successIndex.get(externalId) ?? []) : [];
      const failureData = externalId ? (failureIndex.get(externalId) ?? []) : [];

      const success = successData.reduce((acc, d) => acc + (d?.value ?? 0), 0);
      const failure = failureData.reduce((acc, d) => acc + (d?.value ?? 0), 0);

      return {
        attackPattern,
        success,
        failure,
        total: success + failure,
        present: successData.length > 0 || failureData.length > 0,
      };
    });
  }, [attackPatterns, successIndex, failureIndex]);

  // Filter stats based on the coverage scope (all / exercised / gaps).
  // Covered is presence-based (technique exercised by an inject), NOT
  // result-based: this matches the header KPI in SecurityCoverageContent and
  // keeps techniques visible (muted, coverage unknown) before any expectation
  // result has been scored - e.g. a simulation that has not run yet.
  const filteredStats = useMemo(() => {
    if (coverageFilter === 'covered') return attackPatternStats.filter(stat => stat.present);
    if (coverageFilter === 'gaps') return attackPatternStats.filter(stat => !stat.present);
    return attackPatternStats;
  }, [attackPatternStats, coverageFilter]);

  const onAttackPatternBoxClick = useCallback((stat: AttackPatternStats) => {
    // Deterministic drill-down scope: the clicked technique plus ALL of its
    // sub-techniques from the referential. Using the raw aggregation bucket
    // keys instead would silently drop any bucket truncated out of the terms
    // aggregation, making the list disagree with the tile.
    const clickedId = stat.attackPattern.attack_pattern_id;
    const subTechniqueIds = Object.values(attackPatternMap)
      .filter(attackPattern => attackPattern.attack_pattern_parent === clickedId)
      .map(attackPattern => attackPattern.attack_pattern_id);
    openWidgetDataDrawer({
      widgetId,
      filter_values_map: { [widgetConfig.field]: [clickedId, ...subTechniqueIds] },
      series_index: 0,
    });
  }, [openWidgetDataDrawer, widgetId, widgetConfig.field, attackPatternMap]);

  // Memoize title style
  const titleStyle = useMemo(() => ({ marginBottom: theme.spacing(2) }), [theme]);

  // Hide the whole column when the active coverage scope leaves it empty
  // (e.g. a fully-covered phase under "Gaps", or an untested phase under "Covered").
  if (coverageFilter !== 'all' && filteredStats.length === 0) {
    return null;
  }

  return (
    <div>
      <Typography variant="h5" sx={titleStyle}>
        {killChainPhase.phase_name}
      </Typography>
      <div className={classes.column}>
        {filteredStats.map(stat => (
          <AttackPatternBox
            key={stat.attackPattern.attack_pattern_id}
            attackPatternName={stat.attackPattern.attack_pattern_name}
            attackPatternExternalId={stat.attackPattern.attack_pattern_external_id}
            successRate={stat.total === 0 ? null : (stat.success / stat.total)}
            total={stat.total}
            onClick={() => onAttackPatternBoxClick(stat)}
          />
        ))}
      </div>
    </div>
  );
};

export default memo(KillChainPhaseColumn);
