import { type FunctionComponent } from 'react';

import MarketplaceTabs from '../../../../components/common/marketplace/page/MarketplaceTabs';
import { useFormatter } from '../../../../components/i18n';
import { isFeatureEnabled } from '../../../../utils/utils';

interface Props { active: 'active' | 'available' }

// The Available tab lists the catalogue: no tabs while the catalogue is off.
const ScenariosTabs: FunctionComponent<Props> = ({ active }) => {
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
          to: '/admin/scenarios',
        },
        {
          value: 'available',
          label: t('Available'),
          to: '/admin/scenarios/available',
        },
      ]}
    />
  );
};

export default ScenariosTabs;
