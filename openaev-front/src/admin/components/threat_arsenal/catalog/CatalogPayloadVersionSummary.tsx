import { Button, Chip } from '@filigran/design-system';
import { Typography } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogItemOutput } from '../../../../utils/api-types';

interface Props {
  item: CatalogItemOutput;
  canInstall: boolean;
  installing?: boolean;
  onInstall: () => void;
}

// A catalogue version that is not installed: only its catalogue metadata is known.
const CatalogPayloadVersionSummary: FunctionComponent<Props> = ({ item, canInstall, installing, onInstall }) => {
  const { t, nsdt } = useFormatter();
  const missing = item.catalog_item_missing_requirements ?? [];

  const field = (label: string, value: ReactNode) => (
    <div>
      <Typography variant="h3" gutterBottom>{label}</Typography>
      {value}
    </div>
  );

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 8,
      }}
      >
        <Chip label={t('This version is not installed')} />
        <Button size="sm" disabled={!canInstall || installing} onClick={onInstall}>{t('Install')}</Button>
      </div>
      {field(t('Description'), item.catalog_item_description || '-')}
      {field(t('Author'), item.catalog_item_author || '-')}
      {field(t('Category'), item.catalog_item_category ? t(item.catalog_item_category) : '-')}
      {field(t('Platforms'), (item.catalog_item_platforms ?? []).join(', ') || '-')}
      {field(t('Published'), item.catalog_item_published_at ? nsdt(item.catalog_item_published_at) : '-')}
      {field(t('Changelog'), item.catalog_item_changelog || '-')}
      {missing.length > 0 && field(t('Missing requirements'), missing.join(', '))}
    </div>
  );
};

export default CatalogPayloadVersionSummary;
