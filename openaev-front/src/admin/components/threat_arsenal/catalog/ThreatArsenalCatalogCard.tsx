import { type FunctionComponent } from 'react';

import MarketplaceCard from '../../../../components/common/marketplace/card/MarketplaceCard';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import { useFormatter } from '../../../../components/i18n';
import { type AvailablePayloadActionOutput } from '../../../../utils/api-types';
import { PayloadActionIcon } from './AvailableActionIcon';
import CatalogPayloadInstallAction from './CatalogPayloadInstallAction';

interface Props {
  action: AvailablePayloadActionOutput;
  name: string;
  installed: boolean;
  installing: boolean;
  canInstall: boolean;
  onInstall: () => void;
  onOpen: () => void;
}

const ThreatArsenalCatalogCard: FunctionComponent<Props> = ({ action, name, installed, installing, canInstall, onInstall, onOpen }) => {
  const { t, nsd } = useFormatter();

  return (
    <MarketplaceCard
      testId="threat-arsenal-catalog-card"
      icon={<PayloadActionIcon />}
      title={name}
      overline={action.action_domain ? t(action.action_domain) : undefined}
      description={action.action_payload_description}
      onClick={onOpen}
      info={[
        {
          label: t('Version'),
          value: `v${action.action_payload_version}`,
        },
        {
          label: t('Published'),
          value: action.action_payload_published_at ? nsd(action.action_payload_published_at) : '–',
        },
      ]}
      footerStart={<MarketplaceCardMeta platforms={action.action_platforms} />}
      action={<CatalogPayloadInstallAction installed={installed} installing={installing} canInstall={canInstall} onInstall={onInstall} />}
    />
  );
};

export default ThreatArsenalCatalogCard;
