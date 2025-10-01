import { Chip } from '@mui/material';
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

const inlineStyles = {
  green: {
    backgroundColor: 'rgba(15, 255, 187, 0.1)',
    borderColor: 'rgba(15, 255, 187,1)',
    color: '#ffffff',
  },
  yellow: {
    backgroundColor: 'rgba(255, 207, 15, 0.1)',
    borderColor: 'rgba(255, 207, 15, 0.51)',
    color: '#ffffff',
  },
};

interface Props {
  variant: string;
  privilege: string;
}

const AgentPrivilege: FunctionComponent<Props> = ({ variant, privilege }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const style = variant === 'list' ? classes.chipInList : classes.chip;

  switch (privilege) {
    case 'admin':
      return (
        <Chip
          variant="outlined"
          className={style}
          style={inlineStyles.green}
          label={t('Admin')}
        />
      );
    default:
      return (
        <Chip
          variant="outlined"
          className={style}
          style={inlineStyles.yellow}
          label={t('Standard')}
        />
      );
  }
};

export default AgentPrivilege;
