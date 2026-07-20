import { Chip, Tooltip } from '@mui/material';
import { type FunctionComponent, type ReactElement } from 'react';
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
  tooltipLabel?: string;
  variant?: 'inList';
  isInject?: boolean;
  /** Optional leading icon; inherits the chip's status color. */
  icon?: ReactElement;
}

const ItemStatus: FunctionComponent<ItemStatusProps> = ({
  label,
  status,
  tooltipLabel,
  variant,
  icon,
}) => {
  const { classes } = useStyles();
  const style = variant === 'inList' ? classes.chipInList : classes.chip;
  const classStyle = computeStatusStyle(status);

  return (
    <Tooltip title={tooltipLabel ?? label}>
      <Chip
        classes={{ root: style }}
        style={classStyle}
        label={label}
        icon={icon}
        sx={icon ? {
          '& .MuiChip-icon': {
            color: 'inherit',
            marginLeft: '8px',
          },
        } : undefined}
      />
    </Tooltip>
  );
};

export default ItemStatus;
