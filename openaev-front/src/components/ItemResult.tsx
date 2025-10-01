import { Chip, Tooltip } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

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

const inlineStyles = {
  green: {
    backgroundColor: 'rgba(76, 175, 80, 0.08)',
    color: '#4caf50',
  },
  blue: {
    backgroundColor: 'rgba(92, 123, 245, 0.08)',
    color: '#5c7bf5',
  },
  red: {
    backgroundColor: 'rgba(244, 67, 54, 0.08)',
    color: '#f44336',
  },
  orange: {
    backgroundColor: 'rgba(255, 152, 0, 0.08)',
    color: '#ff9800',
  },
  yellow: {
    backgroundColor: 'rgba(255, 235, 0, 0.08)',
    color: '#ffeb3b',
  },
  purple: {
    backgroundColor: 'rgba(103, 58, 183, 0.08)',
    color: '#673ab7',
  },
  blueGrey: {
    backgroundColor: 'rgba(96, 125, 139, 0.08)',
    color: '#607d8b',
    fontStyle: 'italic',
  },
};

interface ItemStatusProps {
  label: string;
  status?: string | null;
  variant?: 'inList';
  isInject?: boolean;
}

const computeStatusStyle = (status: string | undefined | null) => {
  switch (status) {
    case 'FAILED':
    case 'Failed':
    case 'Not Prevented':
    case 'Not Detected':
      return inlineStyles.red;
    case 'SUCCESS':
    case 'Success':
    case 'Prevented':
    case 'Detected':
      return inlineStyles.green;
    case 'PARTIAL':
    case 'Partial':
    case 'Partially Prevented':
    case 'Partially Detected':
      return inlineStyles.orange;
    default:
      return inlineStyles.blueGrey;
  }
};

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
      <Chip classes={{ root: style }} style={classStyle} label={status} />
    </Tooltip>
  );
};

export default ItemStatus;
