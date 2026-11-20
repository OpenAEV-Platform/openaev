import { type FunctionComponent } from 'react';

import MarketplaceTabs from '../../../../components/common/marketplace/page/MarketplaceTabs';
import { useFormatter } from '../../../../components/i18n';
import { isFeatureEnabled } from '../../../../utils/utils';

interface Props { active: 'active' | 'available' }

// The Available tab lists the catalogue: no tabs while the catalogue is off.
const ThreatArsenalTabs: FunctionComponent<Props> = ({ active }) => {
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
          to: '/admin/threat-arsenal',
        },
        {
          value: 'available',
          label: t('Available'),
          to: '/admin/threat-arsenal/available',
        },
      ]}
    />
  );
};

export default ThreatArsenalTabs;
