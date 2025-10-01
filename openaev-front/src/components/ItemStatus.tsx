import { Chip, Tooltip } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { computeStatusStyle } from '../utils/statusColors';
import { useFormatter } from './i18n';

const useStyles = makeStyles()(() => ({
  chip: {
    fontSize: 12,
    height: 25,
    marginRight: 7,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 150,
  },
  chipInList: {
    fontSize: 12,
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 150,
  },
}));

interface ItemStatusProps {
  label: string;
  status?: string | null;
  variant?: 'inList';
  isInject?: boolean;
}

const ItemStatus: FunctionComponent<ItemStatusProps> = ({
  label,
  status,
  variant,
  isInject = false,
}) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const style = variant === 'inList' ? classes.chipInList : classes.chip;
  const classStyle = computeStatusStyle(status);
  let finalLabel = label;
  if (isInject) {
    if (status === 'SUCCESS') {
      finalLabel = t('INJECT EXECUTED');
    }
  }
  return (
    <Tooltip title={finalLabel}>
      <Chip classes={{ root: style }} style={classStyle} label={finalLabel} />
    </Tooltip>
  );
};

export default ItemStatus;
