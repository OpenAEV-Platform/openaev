import { useState } from 'react';

import { searchCatalogScenarios } from '../../../../actions/catalog/scenario-catalog-actions';
import { CATALOG_ITEM_EXTERNAL_ID } from '../../../../components/common/marketplace/filter/catalogItemSearch';
import { buildFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';

// What a catalogue scenario lacks, read again from the catalogue once an integration is enabled.
const useMissingRequirements = (scenario: CatalogScenarioOutput) => {
  const [missing, setMissing] = useState(scenario.catalog_scenario_missing_requirements);
  const [checking, setChecking] = useState(false);

  const checkAgain = () => {
    setChecking(true);
    searchCatalogScenarios({
      page: 0,
      size: 1,
      filterGroup: {
        mode: 'and',
        filters: [buildFilter(CATALOG_ITEM_EXTERNAL_ID, [scenario.catalog_scenario_external_id], 'eq')],
      },
    })
      .then(({ data }) => {
        const fresh = data.content[0];
        if (fresh) setMissing(fresh.catalog_scenario_missing_requirements);
      })
      .catch(() => undefined)
      .finally(() => setChecking(false));
  };

  return {
    missing,
    checking,
    checkAgain,
  };
};

export default useMissingRequirements;
