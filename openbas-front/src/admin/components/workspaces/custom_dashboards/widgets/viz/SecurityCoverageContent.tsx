import { Box, Checkbox, FormControlLabel } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
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
import { filterByKillChainPhase, resolvedData } from './securityCoverageUtils';

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
  // Fetching data
  // eslint-disable-next-line max-len
  const { attackPatternMap, killChainPhaseMap }: {
    attackPatternMap: Record<string, AttackPattern>;
    killChainPhaseMap: Record<string, KillChainPhase>;
  } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper) => ({
    attackPatternMap: helper.getAttackPatternsMap(),
    killChainPhaseMap: helper.getKillChainPhasesMap(),
  }));

  const resolvedDataSuccess = resolvedData(attackPatternMap, killChainPhaseMap, data.at(0)?.data ?? []);
  const resolvedDataFailure = resolvedData(attackPatternMap, killChainPhaseMap, data.at(1)?.data ?? []);

  const [showCoveredOnly, setShowCoveredOnly] = useLocalStorage<boolean>('widget-' + widgetId, false);

  return (
    <Box
      flex={1}
      display="flex"
      flexDirection="column"
      minHeight={0}
    >
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        padding: theme.spacing(1),
      }}
      >
        <Box className="noDrag">
          <FormControlLabel
            control={(
              <Checkbox
                checked={showCoveredOnly}
                onChange={e => setShowCoveredOnly(e.target.checked)}
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
        {Object.values(killChainPhaseMap).toSorted(sortKillChainPhase)
          .map((phase) => {
            const resolvedDataSuccessByKillChainPhase = filterByKillChainPhase(resolvedDataSuccess, phase.phase_external_id);
            const resolvedDataFailureByKillChainPhase = filterByKillChainPhase(resolvedDataFailure, phase.phase_external_id);
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

export default SecurityCoverageContent;
