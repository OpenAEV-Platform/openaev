import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useContext, useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import type { AttackPatternHelper } from '../../../../../../actions/attack_patterns/attackpattern-helper';
import type { KillChainPhaseHelper } from '../../../../../../actions/kill_chain_phases/killchainphase-helper';
import { useHelper } from '../../../../../../store';
import type { AttackPattern, KillChainPhase } from '../../../../../../utils/api-types';
import { sortAttackPattern } from '../../../../../../utils/attack_patterns/attack_patterns';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import AttackPatternBox from './AttackPatternBox';
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
  successKeys: string[];
  failureKeys: string[];
}

const KillChainPhaseColumn: FunctionComponent<{
  widgetId: string;
  killChainPhase: KillChainPhase;
  showCoveredOnly: boolean;
  resolvedDataSuccess: ResolvedTTPData[];
  resolvedDataFailure: ResolvedTTPData[];
}> = ({ widgetId, killChainPhase, showCoveredOnly, resolvedDataSuccess, resolvedDataFailure }) => {
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
        successKeys: successData.map(d => d.key).filter(Boolean) as string[],
        failureKeys: failureData.map(d => d.key).filter(Boolean) as string[],
      };
    });
  }, [attackPatterns, successIndex, failureIndex]);

  // Filter stats based on showCoveredOnly
  const filteredStats = useMemo(() => {
    if (!showCoveredOnly) return attackPatternStats;
    return attackPatternStats.filter(stat => stat.total > 0);
  }, [attackPatternStats, showCoveredOnly]);

  const onAttackPatternBoxClick = useCallback((stat: AttackPatternStats) => {
    openWidgetDataDrawer({
      widgetId,
      filter_values: [stat.attackPattern.attack_pattern_id, ...stat.successKeys, ...stat.failureKeys],
      series_index: 0,
    });
  }, [openWidgetDataDrawer, widgetId]);

  // Early return if no data and showCoveredOnly
  if (resolvedDataSuccess.length === 0 && resolvedDataFailure.length === 0 && showCoveredOnly) {
    return null;
  }

  // Memoize title style
  const titleStyle = useMemo(() => ({ marginBottom: theme.spacing(2) }), [theme]);

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
