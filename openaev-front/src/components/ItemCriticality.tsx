import { Chip } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { humanizeEnum } from '../admin/components/assets/asset-categories';
import { useFormatter } from './i18n';

const useStyles = makeStyles()(() => ({
  chip: {
    fontSize: 12,
    height: 20,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 100,
  },
}));

// Aligns with the severity palette used across the app (see ItemSeverity) so criticality reads the
// same everywhere: green = low risk, escalating to red for the most critical assets.
const inlineStyles: Record<string, CSSProperties> = {
  green: {
    backgroundColor: 'rgba(76, 175, 80, 0.08)',
    color: '#4caf50',
  },
  blue: {
    backgroundColor: 'rgba(92, 123, 245, 0.08)',
    color: '#5c7bf5',
  },
  orange: {
    backgroundColor: 'rgba(255, 152, 0, 0.08)',
    color: '#ff9800',
  },
  red: {
    backgroundColor: 'rgba(244, 67, 54, 0.08)',
    color: '#f44336',
  },
  blueGrey: {
    backgroundColor: 'rgba(96, 125, 139, 0.08)',
    color: '#607d8b',
    fontStyle: 'italic',
  },
};

const computeCriticalityStyle = (criticality: string | undefined | null): CSSProperties => {
  switch (criticality) {
    case 'LOW':
      return inlineStyles.green;
    case 'MEDIUM':
      return inlineStyles.blue;
    case 'HIGH':
      return inlineStyles.orange;
    case 'VERY_HIGH':
      return inlineStyles.red;
    default:
      return inlineStyles.blueGrey;
  }
};

interface ItemCriticalityProps {
  criticality?: string | null;
  className?: string;
}

const ItemCriticality: FunctionComponent<ItemCriticalityProps> = ({ criticality, className }) => {
  const { classes, cx } = useStyles();
  const { t } = useFormatter();
  // Criticality is only meaningful for assets that carry one; the rest render a neutral dash.
  if (!criticality) {
    return <>-</>;
  }
  return (
    <Chip
      classes={{ root: cx(classes.chip, className) }}
      style={computeCriticalityStyle(criticality)}
      label={t(humanizeEnum(criticality))}
    />
  );
};

export default ItemCriticality;
