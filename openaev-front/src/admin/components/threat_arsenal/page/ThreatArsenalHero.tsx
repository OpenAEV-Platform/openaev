import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';

import { searchThreatArsenalActions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import MarketplaceHero from '../../../../components/common/marketplace/page/MarketplaceHero';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../../components/i18n';

interface Props { action: ReactNode }

// Same header on both tabs: only its action changes.
const ThreatArsenalHero: FunctionComponent<Props> = ({ action }) => {
  const { t } = useFormatter();
  // The whole arsenal, never the current page or filters.
  const [active, setActive] = useState(0);
  useEffect(() => {
    searchThreatArsenalActions(buildSearchPagination({ size: 1 }))
      .then(({ data }: { data: { totalElements?: number } }) => setActive(data?.totalElements ?? 0))
      .catch(() => undefined);
  }, []);

  return (
    <MarketplaceHero
      title={t('Threat Arsenal')}
      subtitle={t('Browse and version the actions your simulations run.')}
      stats={[{
        id: 'active',
        label: t('Active'),
        value: active,
        icon: 'layers-2',
        to: '/admin/threat-arsenal',
      }]}
      action={action}
    />
  );
};

export default ThreatArsenalHero;
