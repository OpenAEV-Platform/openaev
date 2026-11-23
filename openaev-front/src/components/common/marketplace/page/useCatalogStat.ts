import { type SearchPaginationInput } from '../../../../utils/api-types';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';
import { isFeatureEnabled } from '../../../../utils/utils';
import { type MarketplaceHeroStat } from './MarketplaceHero';

// A hero tile counting what a catalogue search finds; none while the catalogue is off.
const useCatalogStat = (
  search: (input: SearchPaginationInput) => Promise<{ data: { totalElements?: number } }>,
  stat: Omit<MarketplaceHeroStat, 'value'>,
): MarketplaceHeroStat[] => {
  const catalog = isFeatureEnabled('CATALOG');
  const total = useSearchTotal(search, catalog);
  if (!catalog) {
    return [];
  }
  return [{
    ...stat,
    value: total ?? 0,
  }];
};

export default useCatalogStat;
