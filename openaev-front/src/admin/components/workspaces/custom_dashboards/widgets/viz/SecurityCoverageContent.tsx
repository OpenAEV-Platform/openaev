import { Box, Checkbox, FormControlLabel } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';
import { useLocalStorage } from 'usehooks-ts';

import type { AttackPatternHelper } from '../../../../../../actions/attack_patterns/attackpattern-helper';
import type { KillChainPhaseHelper } from '../../../../../../actions/kill_chain_phases/killchainphase-helper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type AttackPattern, type EsSeries, type KillChainPhase } from '../../../../../../utils/api-types';
import { sortKillChainPhase } from '../../../../../../utils/kill_chain_phases/kill_chain_phases';
import ColoredPercentageRate from './components/ColoredPercentageRate';
import KillChainPhaseColumn from './KillChainPhaseColumn';
import { buildKillChainPhaseIndex, resolvedData } from './securityCoverageUtils';

const useStyles = makeStyles()(theme => ({
  container: {
    flex: 1,
    overflow: 'auto',
    display: 'flex',
    gap: theme.spacing(1),
    paddingRight: theme.spacing(1),
  },
}));

interface Props {
  widgetId: string;
  data: EsSeries[];
}

const SecurityCoverageContent: FunctionComponent<Props> = ({ widgetId, data }) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();

  // Fetching data - use stable selector
  const { attackPatternMap, killChainPhaseMap }: {
    attackPatternMap: Record<string, AttackPattern>;
    killChainPhaseMap: Record<string, KillChainPhase>;
  } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper) => ({
    attackPatternMap: helper.getAttackPatternsMap(),
    killChainPhaseMap: helper.getKillChainPhasesMap(),
  }));

  // Memoize resolved data computations
  const resolvedDataSuccess = useMemo(
    () => resolvedData(attackPatternMap, killChainPhaseMap, data.at(0)?.data ?? []),
    [attackPatternMap, killChainPhaseMap, data],
  );

  const resolvedDataFailure = useMemo(
    () => resolvedData(attackPatternMap, killChainPhaseMap, data.at(1)?.data ?? []),
    [attackPatternMap, killChainPhaseMap, data],
  );

  // Build indexes for fast phase-based filtering - O(n) once instead of O(n) per phase
  const successByPhase = useMemo(
    () => buildKillChainPhaseIndex(resolvedDataSuccess),
    [resolvedDataSuccess],
  );

  const failureByPhase = useMemo(
    () => buildKillChainPhaseIndex(resolvedDataFailure),
    [resolvedDataFailure],
  );

  // Memoize sorted kill chain phases
  const sortedPhases = useMemo(
    () => Object.values(killChainPhaseMap).toSorted(sortKillChainPhase),
    [killChainPhaseMap],
  );

  const [showCoveredOnly, setShowCoveredOnly] = useLocalStorage<boolean>('widget-' + widgetId, false);

  const handleShowCoveredOnlyChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setShowCoveredOnly(e.target.checked),
    [setShowCoveredOnly],
  );

  // Memoize container padding style
  const headerStyle = useMemo(() => ({
    display: 'flex',
    justifyContent: 'space-between',
    padding: theme.spacing(1),
  }), [theme]);

  return (
    <Box
      flex={1}
      display="flex"
      flexDirection="column"
      minHeight={0}
    >
      <div style={headerStyle}>
        <Box className="noDrag">
          <FormControlLabel
            control={(
              <Checkbox
                checked={showCoveredOnly}
                onChange={handleShowCoveredOnlyChange}
                color="primary"
              />
            )}
            label={t('Show covered TTP only')}
          />
        </Box>
        <div>
          <ColoredPercentageRate />
        </div>
      </div>
      <Box className={classes.container}>
        {sortedPhases.map((phase) => {
          // Use indexed lookups - O(1) instead of O(n) filter
          const resolvedDataSuccessByKillChainPhase = successByPhase.get(phase.phase_external_id) ?? [];
          const resolvedDataFailureByKillChainPhase = failureByPhase.get(phase.phase_external_id) ?? [];
          return (
            <KillChainPhaseColumn
              key={phase.phase_id}
              killChainPhase={phase}
              showCoveredOnly={showCoveredOnly}
              resolvedDataSuccess={resolvedDataSuccessByKillChainPhase}
              resolvedDataFailure={resolvedDataFailureByKillChainPhase}
              widgetId={widgetId}
            />
          );
        })}
      </Box>
    </Box>
  );
};

export default memo(SecurityCoverageContent);
