import { type SearchPaginationInput } from '../../../../utils/api-types';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';
import { isFeatureEnabled } from '../../../../utils/utils';
import { useFormatter } from '../../../i18n';
import { type MarketplaceHeroStat } from './MarketplaceHero';

// The hero tile counting what the Available tab lists; none while the catalogue is off.
const useNotInstalledStat = (
  searchAvailable: (input: SearchPaginationInput) => Promise<{ data: { totalElements?: number } }>,
  availableUrl: string,
): MarketplaceHeroStat[] => {
  const { t } = useFormatter();
  const catalog = isFeatureEnabled('CATALOG');
  const notInstalled = useSearchTotal(searchAvailable, catalog);
  if (!catalog) {
    return [];
  }
  return [{
    id: 'not-installed',
    label: t('Not installed'),
    value: notInstalled ?? 0,
    icon: 'download',
    to: availableUrl,
  }];
};

export default useNotInstalledStat;
