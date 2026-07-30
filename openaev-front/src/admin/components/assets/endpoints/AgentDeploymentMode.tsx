import { Chip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';

const useStyles = makeStyles()(() => ({
  chip: {
    fontSize: 20,
    borderRadius: 4,
  },
  chipInList: {
    fontSize: 12,
    height: 20,
    float: 'left',
    borderRadius: 4,
    marginLeft: 5,
  },
}));

interface Props {
  variant: string;
  mode: string;
}

const AgentDeploymentMode: FunctionComponent<Props> = ({ variant, mode }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();
  const style = variant === 'list' ? classes.chipInList : classes.chip;

  const inlineStyles = {
    blue: {
      backgroundColor: 'rgba(15, 91, 255, 0.1)',
      borderColor: 'rgba(15, 91, 255, 1)',
      color: theme.palette.text.primary,
    },
    purple: {
      backgroundColor: 'rgba(249, 138, 247, 0.1)',
      borderColor: 'rgba(249, 138, 247, 0.51)',
      color: theme.palette.text.primary,
    },
  };

  switch (mode) {
    case 'session':
      return (
        <Chip
          variant="outlined"
          className={style}
          style={inlineStyles.blue}
          label={t('Session')}
        />
      );
    default:
      return (
        <Chip
          variant="outlined"
          className={style}
          style={inlineStyles.purple}
          label={t('Service')}
        />
      );
  }
};

export default AgentDeploymentMode;
