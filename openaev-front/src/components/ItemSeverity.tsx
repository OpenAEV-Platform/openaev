import { alpha, Chip, useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

const useStyles = makeStyles()(() => ({
  chip: {
    fontSize: 12,
    height: 25,
    marginRight: 7,
    borderRadius: 4,
    width: 100,
  },
  chipInList: {
    fontSize: 12,
    height: 20,
    float: 'left',
    borderRadius: 4,
    width: 100,
  },
}));

interface ItemSeverityProps {
  label: string;
  severity?: string | null;
  variant?: 'inList';
}

const ItemSeverity: FunctionComponent<ItemSeverityProps> = ({
  label,
  severity,
  variant,
}) => {
  const { classes } = useStyles();
  const theme = useTheme();
  const style = variant === 'inList' ? classes.chipInList : classes.chip;

  const getSeverityColor = () => {
    switch (severity) {
      case 'low':
        return theme.palette.severity?.low ?? '#16AD34';
      case 'medium':
        return theme.palette.severity?.medium ?? '#E1B823';
      case 'high':
        return theme.palette.severity?.high ?? '#E6700F';
      case 'critical':
        return theme.palette.severity?.critical ?? '#EE3838';
      default:
        return theme.palette.severity?.none ?? '#607d8b';
    }
  };

  const color = getSeverityColor();
  const inlineStyle = {
    backgroundColor: alpha(color, 0.2),
    color,
  };

  return (
    <Chip classes={{ root: style }} style={inlineStyle} label={label} />
  );
};

export default ItemSeverity;
