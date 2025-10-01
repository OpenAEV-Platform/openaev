import { Chip, Tooltip } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { type AttackPattern, type AttackPatternSimple } from '../utils/api-types';

const useStyles = makeStyles()(theme => ({
  chip: {
    fontSize: 12,
    height: 18,
    marginRight: theme.spacing(1),
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 180,
  },
}));

type Props = { attackPattern: AttackPattern | AttackPatternSimple };

const AttackPatternChip = (props: Props) => {
  const { classes } = useStyles();
  const attackPattern = props.attackPattern;
  return (
    <Tooltip key={attackPattern.attack_pattern_id} title={`[${attackPattern.attack_pattern_external_id}] ${attackPattern.attack_pattern_name}`}>
      <Chip
        variant="outlined"
        classes={{ root: classes.chip }}
        color="primary"
        label={`[${attackPattern.attack_pattern_external_id}] ${attackPattern.attack_pattern_name}`}
      />
    </Tooltip>
  );
};

export default AttackPatternChip;
