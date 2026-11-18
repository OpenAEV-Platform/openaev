import { type ReactNode } from 'react';

import MarketplaceCard from '../../../../components/common/marketplace/card/MarketplaceCard';
import { useFormatter } from '../../../../components/i18n';
import usePlatformVersion from '../../../../utils/hooks/usePlatformVersion';
import { type ConnectorItem } from './catalog-facets';
import { CollapsedUseCaseChips, ConnectorLogo } from './ConnectorParts';
import useConnectorTypeLabels from './useConnectorTypeLabels';

interface Props {
  connector: ConnectorItem;
  /** End of the card footer: deploy button, or instance status and migrate action. */
  action?: ReactNode;
}

const CatalogConnectorCard = ({ connector, action }: Props) => {
  const { t, nsd } = useFormatter();
  const platformVersion = usePlatformVersion();
  const typeLabels = useConnectorTypeLabels();
  return (
    <MarketplaceCard
      testId="connector-card"
      icon={<ConnectorLogo connector={connector} />}
      overline={typeLabels[connector.type]}
      title={connector.title}
      // Support as OpenCTI: verified means supported by Filigran.
      subtitle={connector.verified ? `${t('by')} Filigran` : `${t('by')} ${t('Community')}`}
      description={connector.description}
      chips={<CollapsedUseCaseChips useCases={connector.useCases} />}
      action={action}
      info={[
        {
          label: t('Version'),
          // Tags such as "rolling" are not version numbers; a built-in connector ships with the platform.
          value: connector.version
            ? `${/^\d/.test(connector.version) ? 'v' : ''}${connector.version}`
            : [t('Built-in'), platformVersion && `v${platformVersion}`].filter(Boolean).join(' · '),
        },
        ...(connector.lastVerifiedAt
          ? [{
              label: t('Verified'),
              value: nsd(connector.lastVerifiedAt),
            }]
          : []),
        ...(connector.lastSeen
          ? [{
              label: t('Last Seen'),
              value: connector.lastSeen,
            }]
          : []),
      ]}
      linkTo={connector.detailUrl}
    />
  );
};

export default CatalogConnectorCard;
