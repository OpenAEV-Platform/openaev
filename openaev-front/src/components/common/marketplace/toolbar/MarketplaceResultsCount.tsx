import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../i18n';

const MarketplaceResultsCount: FunctionComponent<{ count: number }> = ({ count }) => {
  const { t } = useFormatter();
  return (
    <span data-testid="marketplace-results-count">
      <Chip label={String(count === 1 ? t('1 result') : t('{count} results', { count }))} />
    </span>
  );
};

export default MarketplaceResultsCount;
