import { Button, Chip } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { type CatalogPayload } from '../../../../actions/catalog/payload-catalog-preview';
import type { DomainHelper } from '../../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Domain } from '../../../../utils/api-types';
import InjectIcon from '../../common/injects/InjectIcon';

interface Props {
  item: CatalogPayload;
  installed: boolean;
  canInstall: boolean;
  onInstall: () => void;
}

const ThreatArsenalCatalogCard: FunctionComponent<Props> = ({ item, installed, canInstall, onInstall }) => {
  const { t, nsd } = useFormatter();
  const theme = useTheme();
  const domain: Domain | undefined = useHelper(
    (helper: DomainHelper) => helper.getDomains().find(d => d.domain_name === item.catalog_item_domain),
  );
  const accent = domain?.domain_color ?? theme.palette.primary.main;

  return (
    <MarketplaceCard
      accent={accent}
      testId="threat-arsenal-catalog-card"
      icon={<InjectIcon type={item.catalog_item_type} isPayload variant="list" />}
      title={item.catalog_item_name}
      chips={(
        <>
          <Chip label={t(item.catalog_item_domain)} color={accent} />
          <Chip label={`v${item.catalog_item_version}`} />
          <Chip label={`${t('by')} ${item.catalog_item_author}`} />
        </>
      )}
      platforms={item.catalog_item_platforms}
      footerRight={installed
        ? <Chip label={t('Installed')} severity="low" />
        : (
            <Button size="sm" priority="secondary" onClick={onInstall} disabled={!canInstall}>
              {t('Install')}
            </Button>
          )}
      caption={`${t('Published')} ${nsd(item.catalog_item_published_at)}`}
    />
  );
};

export default ThreatArsenalCatalogCard;
