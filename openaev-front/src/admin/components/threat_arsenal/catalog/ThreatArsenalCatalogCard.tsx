import { Button, Chip } from '@filigran/design-system';
import { TerminalOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import type { DomainHelper } from '../../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type CatalogItemOutput, type Domain } from '../../../../utils/api-types';
import { getStatusLabel, type PayloadStatus, STATUS_CHIP_SEVERITY } from '../threatArsenalStatusUtils';
import { INSTALLED_PATH, installedState } from './notifyPayloadInstalled';

interface Props {
  item: CatalogItemOutput;
  installed: boolean;
  installing: boolean;
  canInstall: boolean;
  onInstall: () => void;
  onOpen: () => void;
}

const ThreatArsenalCatalogCard: FunctionComponent<Props> = ({ item, installed, installing, canInstall, onInstall, onOpen }) => {
  const { t, nsd } = useFormatter();
  const theme = useTheme();
  const domainName = item.catalog_item_category;
  const domain: Domain | undefined = useHelper(
    (helper: DomainHelper) => helper.getDomains().find(d => d.domain_name === domainName),
  );
  const accent = domain?.domain_color ?? theme.palette.primary.main;
  const statusLabel = getStatusLabel(item.catalog_item_status);
  const title = item.catalog_item_name ?? item.catalog_item_external_id;
  const statusChip = statusLabel && <Chip label={t(statusLabel)} severity={STATUS_CHIP_SEVERITY[item.catalog_item_status as PayloadStatus]} />;

  return (
    <MarketplaceCard
      accent={accent}
      testId="threat-arsenal-catalog-card"
      icon={<TerminalOutlined color="primary" />}
      title={title}
      overline={domainName ? t(domainName) : undefined}
      subtitle={item.catalog_item_author ? `${t('by')} ${item.catalog_item_author}` : undefined}
      description={item.catalog_item_description ?? undefined}
      chips={undefined}
      platforms={item.catalog_item_platforms ?? []}
      onClick={onOpen}
      footerRight={(
        <span
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          {statusChip}
          {installed && <Chip label={t('Installed')} severity="low" />}
        </span>
      )}
      action={(
        <span onClick={event => event.stopPropagation()}>
          {installed
            ? (
                <Button size="sm" priority="secondary" asChild>
                  <Link to={INSTALLED_PATH} state={installedState(title)}>{t('Open')}</Link>
                </Button>
              )
            : (
                <Button size="sm" onClick={onInstall} disabled={!canInstall} loading={installing}>
                  {t('Install')}
                </Button>
              )}
        </span>
      )}
      caption={[
        `v${item.catalog_item_version}`,
        item.catalog_item_published_at ? `${t('Published')} ${nsd(item.catalog_item_published_at)}` : null,
      ].filter(Boolean).join(' · ')}
    />
  );
};

export default ThreatArsenalCatalogCard;
