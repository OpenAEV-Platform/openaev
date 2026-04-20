import { Chip, Tooltip } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { computeStatusStyle } from '../utils/statusUtils';

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
}) => {
  const { classes } = useStyles();
  const style = variant === 'inList' ? classes.chipInList : classes.chip;
  const classStyle = computeStatusStyle(status);

  return (
    <Tooltip title={label}>
      <Chip classes={{ root: style }} style={classStyle} label={label} />
    </Tooltip>
  );
};

export default ItemStatus;
