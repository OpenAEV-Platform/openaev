import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type AttackPattern, type InjectExpectationResultsByAttackPattern, type KillChainPhase } from '../../../../utils/api-types';
import AttackPatternBox from './AttackPatternBox';

const useStyles = makeStyles()(() => ({
  column: {
    display: 'flex',
    flexDirection: 'column',
    gap: 4,
  },
}));

interface KillChainPhaseComponentProps {
  goToLink?: string;
  killChainPhase: KillChainPhase;
  attackPatterns: AttackPattern[];
  injectResults: InjectExpectationResultsByAttackPattern[];
  dummy?: boolean;
}

const KillChainPhaseColumn: FunctionComponent<KillChainPhaseComponentProps> = ({
  goToLink,
  killChainPhase,
  attackPatterns,
  injectResults,
  dummy,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  // Attack Pattern
  const sortAttackPattern = (attackPattern1: AttackPattern, attackPattern2: AttackPattern) => {
    if (attackPattern1.attack_pattern_name < attackPattern2.attack_pattern_name) {
      return -1;
    }
    if (attackPattern1.attack_pattern_name > attackPattern2.attack_pattern_name) {
      return 1;
    }
    return 0;
  };
  // Inject Results
  const getInjectResult = (attack: AttackPattern) => {
    return injectResults.find(injectResult => injectResult.inject_attack_pattern?.includes(attack.attack_pattern_id));
  };
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{
        fontSize: 15,
        textAlign: 'center',
        marginBottom: 20,
        color: dummy ? theme.palette.text?.disabled : theme.palette.text?.primary,
      }}
      >
        {killChainPhase.phase_name}
      </div>
      <div className={classes.column}>
        {[...attackPatterns].sort(sortAttackPattern)
          .map(attackPattern => (
            <AttackPatternBox
              goToLink={goToLink}
              key={attackPattern.attack_pattern_id}
              attackPattern={attackPattern}
              injectResult={getInjectResult(attackPattern)}
              dummy={dummy}
            />
          ))}
      </div>
    </div>
  );
};

export default KillChainPhaseColumn;
