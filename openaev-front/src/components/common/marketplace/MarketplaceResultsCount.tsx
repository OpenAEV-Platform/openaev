import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../i18n';

// Result count of the marketplace toolbars (integrations, threat arsenal, scenarios).
const MarketplaceResultsCount: FunctionComponent<{ count: number }> = ({ count }) => {
  const { t } = useFormatter();
  return <Chip label={String(count === 1 ? t('1 result') : t('{count} results', { count }))} />;
};

export default MarketplaceResultsCount;
