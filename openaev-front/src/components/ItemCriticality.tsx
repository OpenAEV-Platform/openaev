import { Chip } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { humanizeEnum } from '../admin/components/assets/asset-categories';
import { criticalityStyle } from './criticalityColor';
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
      style={criticalityStyle(criticality)}
      label={t(humanizeEnum(criticality))}
    />
  );
};

export default ItemCriticality;
