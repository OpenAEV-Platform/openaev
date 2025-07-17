import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import type { AttackPatternHelper } from '../../../../../../actions/attack_patterns/attackpattern-helper';
import type { KillChainPhaseHelper } from '../../../../../../actions/kill_chain_phases/killchainphase-helper';
import { useHelper } from '../../../../../../store';
import type { AttackPattern, KillChainPhase } from '../../../../../../utils/api-types';
import { sortAttackPattern } from '../../../../../../utils/attack_patterns/attack_patterns';
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

const KillChainPhaseColumn: FunctionComponent<{
  killChainPhase: KillChainPhase;
  showCoveredOnly: boolean;
  resolvedDataSuccess: ResolvedTTPData[];
  resolvedDataFailure: ResolvedTTPData[];
}> = ({ killChainPhase, showCoveredOnly, resolvedDataSuccess, resolvedDataFailure }) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();

  // Fetching data
  // eslint-disable-next-line max-len
  const { attackPatternMap }: { attackPatternMap: Record<string, AttackPattern> } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper) => ({ attackPatternMap: helper.getAttackPatternsMap() }));

  const attackPatterns: AttackPattern[] = Object.values(attackPatternMap)
    .filter((attackPattern: AttackPattern) => attackPattern.attack_pattern_kill_chain_phases?.includes(killChainPhase.phase_id))
    .filter((attackPattern: AttackPattern) => attackPattern.attack_pattern_parent === null); // Remove sub techniques

  if (resolvedDataSuccess.length === 0 && resolvedDataFailure.length === 0 && showCoveredOnly) {
    return (<></>);
  }

  return (
    <div>
      <Typography
        variant="h5"
        sx={{ marginBottom: theme.spacing(2) }}
      >
        {killChainPhase.phase_name}
      </Typography>
      <div className={classes.column}>
        {attackPatterns.toSorted(sortAttackPattern)
          .map((attackPattern) => {
            const resolvedDataSuccessForTTP = resolvedDataSuccess.filter(d => d.attack_pattern_external_id === attackPattern.attack_pattern_external_id);
            const resolvedDataFailureForTTP = resolvedDataFailure.filter(d => d.attack_pattern_external_id === attackPattern.attack_pattern_external_id);
            const success = resolvedDataSuccessForTTP.length;
            const failure = resolvedDataFailureForTTP.length;
            const total = success + failure;

            if (showCoveredOnly && total == 0) {
              return (<></>);
            }

            return (
              <AttackPatternBox
                key={attackPattern.attack_pattern_id}
                attackPatternName={attackPattern.attack_pattern_name}
                attackPatternExerternalId={attackPattern.attack_pattern_external_id}
                successRate={total === 0 ? null : (success / total)}
                total={total}
              />
            );
          })}
      </div>
    </div>
  );
};

export default KillChainPhaseColumn;
