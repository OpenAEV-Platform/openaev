import { useEffect, useState } from 'react';

import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';
import { searchCatalogScenariosIn, UPDATE_AVAILABLE } from '../catalog/catalogScenarioInstallation';
import { catalogScenarioType } from '../catalog/catalogScenarioType';
import { SCENARIO_TYPE_CHAINED } from '../scenario/ScenarioType';

// Beyond this many updates available, the count and the banners miss the rest.
const PAGE_SIZE = 500;

/** The newer Filigran version of each installed scenario that can take it, by scenario id: chained ones cannot yet. */
const useScenarioUpdatesAvailable = (reloadKey = 0) => {
  const catalog = isFeatureEnabled('CATALOG');
  const [updates, setUpdates] = useState<Map<string, string>>(new Map());

  const load = () => {
    if (!catalog) return;
    searchCatalogScenariosIn(UPDATE_AVAILABLE)({
      page: 0,
      size: PAGE_SIZE,
    })
      .then(({ data }: { data: { content: CatalogScenarioOutput[] } }) => setUpdates(new Map(data.content
        .filter(item => item.catalog_scenario_installed_scenario_id && catalogScenarioType(item) !== SCENARIO_TYPE_CHAINED)
        .map(item => [item.catalog_scenario_installed_scenario_id as string, item.catalog_scenario_version]))))
      .catch(() => undefined);
  };
  useEffect(load, [catalog, reloadKey]);

  return updates;
};

export default useScenarioUpdatesAvailable;
