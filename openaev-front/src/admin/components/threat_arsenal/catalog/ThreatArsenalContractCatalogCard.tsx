import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import MarketplaceCard from '../../../../components/common/marketplace/card/MarketplaceCard';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import { useFormatter } from '../../../../components/i18n';
import { type AvailableIntegrationActionOutput } from '../../../../utils/api-types';
import { IntegrationActionIcon } from './AvailableActionIcon';
import { integrationUrl } from './availableActions';

interface Props {
  action: AvailableIntegrationActionOutput;
  name: string;
}

// The action comes with the integration: the card leads to the integration page, where the user deploys it.
const ThreatArsenalContractCatalogCard: FunctionComponent<Props> = ({ action, name }) => {
  const { t } = useFormatter();
  const techniques = action.action_attack_patterns;

  return (
    <MarketplaceCard
      testId="threat-arsenal-contract-catalog-card"
      icon={<IntegrationActionIcon action={action} />}
      title={name}
      overline={action.action_domain ? t(action.action_domain) : undefined}
      subtitle={`${t('by')} ${action.action_integration_title}`}
      description={t('Deploy this integration to use this action.')}
      // The card is a link over its whole surface: the full list is the chip's title, not a tooltip.
      chips={techniques.length > 0 && (
        <span className="inline-flex" title={techniques.join(', ')}>
          <Chip label={techniques.length > 1 ? `${techniques[0]} +${techniques.length - 1}` : techniques[0]} />
        </span>
      )}
      footerStart={<MarketplaceCardMeta platforms={action.action_platforms} />}
      linkTo={integrationUrl(action)}
    />
  );
};

export default ThreatArsenalContractCatalogCard;
