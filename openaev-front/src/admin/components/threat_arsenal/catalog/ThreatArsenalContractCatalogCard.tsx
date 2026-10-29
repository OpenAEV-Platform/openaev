import { Button, Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ExtensionOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { useHref } from 'react-router';

import { type CatalogInjectorContract } from '../../../../actions/catalog/injector-contract-catalog-actions';
import type { DomainHelper } from '../../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Domain } from '../../../../utils/api-types';

interface Props { contract: CatalogInjectorContract }

// Installing an injector contract means enabling the integration that provides it.
const ThreatArsenalContractCatalogCard: FunctionComponent<Props> = ({ contract }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const domains = contract.contract_domains ?? [];
  const primaryDomainName = domains[0];
  const domain: Domain | undefined = useHelper(
    (helper: DomainHelper) => helper.getDomains().find(d => d.domain_name === primaryDomainName),
  );
  const accent = domain?.domain_color ?? theme.palette.primary.main;
  const integrationHref = useHref(`/admin/integrations/catalog/${contract.catalog_connector_id}`);
  const openIntegration = () => window.open(integrationHref, '_blank', 'noopener');

  return (
    <MarketplaceCard
      accent={accent}
      testId="threat-arsenal-contract-catalog-card"
      icon={contract.catalog_connector_logo_url
        ? (
            <img
              src={`/api/images/catalog/connectors/logos/${contract.catalog_connector_logo_url}`}
              alt={contract.catalog_connector_title ?? ''}
              style={{
                width: 28,
                height: 28,
                objectFit: 'contain',
              }}
            />
          )
        : <ExtensionOutlined color="primary" />}
      title={contract.contract_label ?? contract.contract_id}
      overline={primaryDomainName ? t(primaryDomainName) : undefined}
      subtitle={contract.catalog_connector_title ? `${t('by')} ${contract.catalog_connector_title}` : undefined}
      chips={(
        <>
          {domains.length > 1 && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Chip label={`+${domains.length - 1}`} />
              </TooltipTrigger>
              <TooltipContent>{domains.slice(1).map(name => t(name)).join(', ')}</TooltipContent>
            </Tooltip>
          )}
        </>
      )}
      platforms={contract.contract_platforms ?? []}
      onClick={openIntegration}
      action={(
        <span onClick={event => event.stopPropagation()}>
          <Button size="sm" onClick={openIntegration}>
            {t('Enable integration')}
          </Button>
        </span>
      )}
      caption={contract.catalog_connector_title
        ? t('Requires the {integration} integration', { integration: contract.catalog_connector_title })
        : t('Provided by an integration')}
    />
  );
};

export default ThreatArsenalContractCatalogCard;
