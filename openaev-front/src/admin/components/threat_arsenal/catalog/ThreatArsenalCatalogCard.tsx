import { Button, Chip } from '@filigran/design-system';
import { TerminalOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import type { DomainHelper } from '../../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
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
  // An older version of this payload is installed: the card offers the update.
  const olderInstalled = (item.catalog_item_installed_versions ?? []).filter(v => v !== item.catalog_item_version);
  const statusChip = statusLabel && <Chip label={t(statusLabel)} severity={STATUS_CHIP_SEVERITY[item.catalog_item_status as PayloadStatus]} />;

  return (
    // Same anatomy as the Active cards (Figma DS Card), with what matters before installing.
    <MarketplaceCard
      accent={accent}
      testId="threat-arsenal-catalog-card"
      icon={<TerminalOutlined color="primary" />}
      title={title}
      overline={domainName ? t(domainName) : undefined}
      subtitle={item.catalog_item_author ? `${t('by')} ${item.catalog_item_author}` : undefined}
      description={item.catalog_item_description ?? undefined}
      height="100%"
      info={[
        {
          label: t('Version'),
          value: `v${item.catalog_item_version}`,
        },
        {
          label: t('Source'),
          value: t('Filigran'),
        },
        {
          label: t('Published'),
          value: item.catalog_item_published_at ? nsd(item.catalog_item_published_at) : '–',
        },
      ]}
      chips={null}
      platforms={item.catalog_item_platforms ?? []}
      statusRow={(installed || olderInstalled.length > 0) && (
        installed
          ? <Chip label={t('Installed')} severity="low" />
          : <Chip label={t('v{version} installed', { version: olderInstalled[0] })} />
      )}
      metaStart={(
        <>
          {statusChip}
          {(item.catalog_item_platforms ?? []).slice(0, 3).map(platform => <PlatformIcon key={platform} width={16} platform={platform} tooltip />)}
        </>
      )}
      onClick={onOpen}
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
                  {olderInstalled.length > 0 ? t('Update') : t('Install')}
                </Button>
              )}
        </span>
      )}
    />
  );
};

export default ThreatArsenalCatalogCard;
