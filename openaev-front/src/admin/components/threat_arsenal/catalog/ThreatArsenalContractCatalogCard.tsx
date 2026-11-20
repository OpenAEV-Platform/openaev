import { Chip } from '@filigran/design-system';
import { ExtensionOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import MarketplaceCard from '../../../../components/common/marketplace/card/MarketplaceCard';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import { useFormatter } from '../../../../components/i18n';
import { type AvailableIntegrationActionOutput } from '../../../../utils/api-types';
import { catalogConnectorUrl } from '../../integrations/catalog_connectors/catalog-facets';

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
      icon={action.action_integration_logo_url
        ? <img src={`/api/images/catalog/connectors/logos/${action.action_integration_logo_url}`} alt={action.action_integration_title} />
        : <ExtensionOutlined color="primary" />}
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
      linkTo={catalogConnectorUrl(action.action_integration_id)}
    />
  );
};

export default ThreatArsenalContractCatalogCard;
