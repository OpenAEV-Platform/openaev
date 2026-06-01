import { Chip } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../components/i18n';

const useStyles = makeStyles()(() => ({
  chip: {
    fontSize: 20,
    fontWeight: 800,
    textTransform: 'uppercase',
    borderRadius: 4,
  },
  chipInList: {
    fontSize: 12,
    lineHeight: '12px',
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 120,
  },
}));

const inlineStyles = {
  green: {
    backgroundColor: 'rgba(76, 175, 80, 0.08)',
    color: '#4caf50',
  },
  red: {
    backgroundColor: 'rgba(244, 67, 54, 0.08)',
    color: '#f44336',
  },
  orange: {
    backgroundColor: 'rgba(246,177,27,0.08)',
    color: '#f19710',
  },
};

interface Props {
  variant: string;
  status: 'Active' | 'Inactive' | 'Agentless';
}

const AssetStatus: FunctionComponent<Props> = ({ variant, status = 'Active' }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const style = variant === 'list' ? classes.chipInList : classes.chip;

  switch (status) {
    case 'Inactive':
      return (
        <Chip
          className={style}
          style={inlineStyles.red}
          label={t('Inactive')}
        />
      );
    case 'Agentless':
      return (
        <Chip
          className={style}
          style={inlineStyles.orange}
          label={t('Agentless')}
        />
      );
    default:
      return (
        <Chip
          className={style}
          style={inlineStyles.green}
          label={t('Active')}
        />
      );
  }
};

export default AssetStatus;
