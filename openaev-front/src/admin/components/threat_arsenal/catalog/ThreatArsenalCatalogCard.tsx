import { Button, Chip } from '@filigran/design-system';
import { TerminalOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import MarketplaceCard from '../../../../components/common/marketplace/card/MarketplaceCard';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import { type CatalogPayloadOutput } from '../../../../utils/api-types';

interface Props {
  payload: CatalogPayloadOutput;
  installed: boolean;
  installing: boolean;
  canInstall: boolean;
  onInstall: () => void;
  onOpen: () => void;
}

const ThreatArsenalCatalogCard: FunctionComponent<Props> = ({ payload, installed, installing, canInstall, onInstall, onOpen }) => {
  const { t, nsd } = useFormatter();

  return (
    <MarketplaceCard
      testId="threat-arsenal-catalog-card"
      icon={<TerminalOutlined color="primary" />}
      title={payload.catalog_payload_name ?? payload.catalog_payload_external_id}
      overline={payload.catalog_payload_domain ? t(payload.catalog_payload_domain) : undefined}
      description={payload.catalog_payload_description}
      onClick={onOpen}
      info={[
        {
          label: t('Version'),
          value: `v${payload.catalog_payload_version}`,
        },
        {
          label: t('Published'),
          value: payload.catalog_payload_published_at ? nsd(payload.catalog_payload_published_at) : '–',
        },
      ]}
      footerStart={(
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 4,
        }}
        >
          {payload.catalog_payload_platforms.map(platform => <PlatformIcon key={platform} width={16} platform={platform} tooltip />)}
        </div>
      )}
      action={installed
        ? <Chip label={t('Installed')} severity="low" />
        : canInstall && <Button size="sm" loading={installing} onClick={onInstall}>{t('Install')}</Button>}
    />
  );
};

export default ThreatArsenalCatalogCard;
