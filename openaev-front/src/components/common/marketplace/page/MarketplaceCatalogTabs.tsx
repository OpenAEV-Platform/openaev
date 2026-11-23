import { type FunctionComponent } from 'react';

import { isFeatureEnabled } from '../../../../utils/utils';
import { useFormatter } from '../../../i18n';
import MarketplaceTabs from './MarketplaceTabs';

interface Props {
  active: 'active' | 'available';
  // The Active tab's route; the Available tab lives under it.
  baseUrl: string;
}

// The Available tab lists the catalogue: no tabs while the catalogue is off.
const MarketplaceCatalogTabs: FunctionComponent<Props> = ({ active, baseUrl }) => {
  const { t } = useFormatter();
  if (!isFeatureEnabled('CATALOG')) {
    return null;
  }
  return (
    <MarketplaceTabs
      active={active}
      tabs={[
        {
          value: 'active',
          label: t('Active'),
          to: baseUrl,
        },
        {
          value: 'available',
          label: t('Available'),
          to: `${baseUrl}/available`,
        },
      ]}
    />
  );
};

export default MarketplaceCatalogTabs;
