import { type FunctionComponent, type ReactNode } from 'react';

import { searchThreatArsenalActions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import MarketplaceHero from '../../../../components/common/marketplace/page/MarketplaceHero';
import { useFormatter } from '../../../../components/i18n';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';

interface Props { action: ReactNode }

// Same header on both tabs: only its action changes.
const ThreatArsenalHero: FunctionComponent<Props> = ({ action }) => {
  const { t } = useFormatter();
  // The whole arsenal, never the current page or filters.
  const active = useSearchTotal(searchThreatArsenalActions);

  return (
    <MarketplaceHero
      title={t('Threat Arsenal')}
      subtitle={t('Browse and version the actions your simulations run.')}
      stats={[{
        id: 'active',
        label: t('Active'),
        value: active ?? 0,
        icon: 'layers-2',
        to: '/admin/threat-arsenal',
      }]}
      action={action}
    />
  );
};

export default ThreatArsenalHero;
