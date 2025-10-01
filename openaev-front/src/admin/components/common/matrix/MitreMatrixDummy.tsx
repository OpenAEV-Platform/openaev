import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import { type KillChainPhaseHelper } from '../../../../actions/kill_chain_phases/killchainphase-helper';
import { useHelper } from '../../../../store';
import { type AttackPattern, type KillChainPhase } from '../../../../utils/api-types';
import { random } from '../../../../utils/number';
import KillChainPhaseColumn from './KillChainPhaseColumn';

const useStyles = makeStyles()(() => ({
  container: {
    width: '100%',
    display: 'flex',
    gap: 20,
    overflowX: 'auto',
    // test
    animation: 'detect-scroll linear',
    animationTimeline: 'scroll(self inline)',
  },
}));

const MitreMatrixDummy: FunctionComponent = () => {
  // Standard hooks
  const { classes } = useStyles();
  // Fetching data
  const { attackPatterns, killChainPhaseMap }: {
    attackPatterns: AttackPattern[];
    killChainPhaseMap: Record<string, KillChainPhase>;
  } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper) => ({
    attackPatterns: helper.getAttackPatterns(),
    killChainPhaseMap: helper.getKillChainPhasesMap(),
  }));

  // Attack Pattern & Kill Chain Phase
  const sortKillChainPhase = (k1: KillChainPhase, k2: KillChainPhase) => {
    return (k1.phase_order ?? 0) - (k2.phase_order ?? 0);
  };
  const getAttackPatterns = (killChainPhase: KillChainPhase) => {
    return R.take(random(3, 6), attackPatterns.filter((attackPattern: AttackPattern) => attackPattern.attack_pattern_kill_chain_phases?.includes(killChainPhase.phase_id)));
  };
  return (
    <div className={classes.container}>
      {R.values(killChainPhaseMap).sort(sortKillChainPhase)
        .map((killChainPhase: KillChainPhase) => (
          <KillChainPhaseColumn
            key={killChainPhase.phase_id}
            killChainPhase={killChainPhase}
            attackPatterns={getAttackPatterns(killChainPhase)}
            injectResults={[]}
            dummy={true}
          />
        ))}
    </div>
  );
};

export default MitreMatrixDummy;
