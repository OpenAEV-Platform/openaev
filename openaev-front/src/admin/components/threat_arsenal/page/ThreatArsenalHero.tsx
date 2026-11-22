import { type FunctionComponent, type ReactNode } from 'react';

import { searchAvailableActions } from '../../../../actions/catalog/payload-catalog-actions';
import { searchThreatArsenalActions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import MarketplaceHero from '../../../../components/common/marketplace/page/MarketplaceHero';
import useCatalogStat from '../../../../components/common/marketplace/page/useCatalogStat';
import { useFormatter } from '../../../../components/i18n';
import { THREAT_ARSENAL_BASE_URL } from '../../../../constants/BaseUrls';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';

interface Props { action?: ReactNode }

// Same header on both tabs: only its action changes.
const ThreatArsenalHero: FunctionComponent<Props> = ({ action }) => {
  const { t } = useFormatter();
  // The whole arsenal and catalogue, never the current page or filters.
  const active = useSearchTotal(searchThreatArsenalActions) ?? 0;
  const notInstalled = useCatalogStat(searchAvailableActions, {
    id: 'not-installed',
    label: t('Not installed'),
    icon: 'download',
    to: `${THREAT_ARSENAL_BASE_URL}/available`,
  });

  return (
    <MarketplaceHero
      title={t('Threat Arsenal')}
      subtitle={t('Browse, install and version the actions your simulations run.')}
      stats={[
        {
          id: 'active',
          label: t('Active'),
          value: active,
          icon: 'layers-2',
          to: THREAT_ARSENAL_BASE_URL,
        },
        ...notInstalled,
      ]}
      action={action}
    />
  );
};

export default ThreatArsenalHero;
