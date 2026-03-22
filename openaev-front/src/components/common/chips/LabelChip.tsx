import { Chip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../i18n';

const useStyles = makeStyles()(theme => ({
  labelChip: {
    borderRadius: theme.borderRadius,
    marginBottom: 5,
    height: 20,
  },
}));

interface Props {
  label: string;
  color: string;
  size?: number;
}

const LabelChip: FunctionComponent<Props> = ({
  label,
  color,
  size = 80,
}) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const theme = useTheme();

  const chipStyle = theme.palette.labelChipMap.get(color) ?? {
    backgroundColor: 'rgba(149, 150, 157, 0.2)',
    color: '#95969D',
  };

  return (
    <Chip
      className={classes.labelChip}
      style={chipStyle}
      sx={{ width: size }}
      label={t(label)}
    />
  );
};
export default LabelChip;
