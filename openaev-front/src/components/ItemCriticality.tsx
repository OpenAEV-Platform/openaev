import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { humanizeEnum } from '../admin/components/assets/asset-categories';
import { criticalitySeverity } from './criticalityColor';
import { useFormatter } from './i18n';

interface ItemCriticalityProps {
  criticality?: string | null;
  className?: string;
}

const ItemCriticality: FunctionComponent<ItemCriticalityProps> = ({ criticality }) => {
  const { t } = useFormatter();
  // Criticality is only meaningful for assets that carry one; the rest render a neutral dash.
  if (!criticality) {
    return <>-</>;
  }
  return (
    <Chip severity={criticalitySeverity(criticality)} label={t(humanizeEnum(criticality))} />
  );
};

export default ItemCriticality;
