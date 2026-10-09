import { Alert } from '@mui/material';

import { useFormatter } from '../../i18n';

// Shown on catalogue tabs while their content is simulated (RFC 0005, before PR 3b).
const MarketplacePreviewNotice = () => {
  const { t } = useFormatter();
  return (
    <Alert severity="info">
      {t('Preview: the catalog content is simulated until the catalog sync is available.')}
    </Alert>
  );
};

export default MarketplacePreviewNotice;
