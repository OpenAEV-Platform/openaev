import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import { type KillChainPhaseHelper } from '../../../../actions/kill_chain_phases/killchainphase-helper';
import { useHelper } from '../../../../store';
import { type AttackPattern, type InjectExpectationResultsByAttackPattern, type KillChainPhase } from '../../../../utils/api-types';
import { sortKillChainPhase } from '../../../../utils/kill_chain_phases/kill_chain_phases';
import KillChainPhaseColumn from './KillChainPhaseColumn';
import MitreMatrixDummy from './MitreMatrixDummy';

const useStyles = makeStyles()(() => ({
  container: {
    width: '100%',
    display: 'flex',
    gap: 20,
    overflowX: 'auto',
    animation: 'detect-scroll linear',
    animationTimeline: 'scroll(self inline)',
  },
}));

interface Props {
  goToLink?: string;
  injectResults: InjectExpectationResultsByAttackPattern[];
}

const MitreMatrix: FunctionComponent<Props> = ({
  goToLink,
  injectResults,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  // Fetching data
  const { attackPatternMap, killChainPhaseMap }: {
    attackPatternMap: Record<string, AttackPattern>;
    killChainPhaseMap: Record<string, KillChainPhase>;
  } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper) => ({
    attackPatternMap: helper.getAttackPatternsMap(),
    killChainPhaseMap: helper.getKillChainPhasesMap(),
  }));

  if (!injectResults) {
    return <MitreMatrixDummy />;
  }

  // Attack Pattern
  const resultAttackPatternIds = R.uniq(
    injectResults
      .filter(injectResult => !!injectResult.inject_attack_pattern)
      .flatMap(injectResult => injectResult.inject_attack_pattern) as unknown as string[],
  );
  const resultAttackPatterns: AttackPattern[] = resultAttackPatternIds.map((attackPatternId: string) => attackPatternMap[attackPatternId])
    .filter((attackPattern: AttackPattern) => !!attackPattern);
  const getAttackPatterns = (killChainPhase: KillChainPhase) => {
    return resultAttackPatterns.filter((attackPattern: AttackPattern) => attackPattern.attack_pattern_kill_chain_phases?.includes(killChainPhase.phase_id));
  };
  // Kill Chain Phase
  const resultKillChainPhases = R.uniq(resultAttackPatterns
    .flatMap(attackPattern => (attackPattern.attack_pattern_kill_chain_phases ?? []))
    .map((killChainPhaseId: string) => killChainPhaseMap[killChainPhaseId])
    .filter(killChainPhase => !!killChainPhase));
  return (
    <div className={classes.container}>
      {[...resultKillChainPhases].sort(sortKillChainPhase)
        .map((killChainPhase: KillChainPhase) => (
          <KillChainPhaseColumn
            key={killChainPhase.phase_id}
            goToLink={goToLink}
            killChainPhase={killChainPhase}
            attackPatterns={getAttackPatterns(killChainPhase)}
            injectResults={injectResults}
          />
        ))}
    </div>
  );
};

export default MitreMatrix;
