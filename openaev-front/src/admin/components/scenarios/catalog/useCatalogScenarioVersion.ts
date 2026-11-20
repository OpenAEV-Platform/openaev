import { useEffect, useState } from 'react';

import { fetchCatalogScenarioVersions } from '../../../../actions/catalog/scenario-catalog-actions';
import { type CatalogScenarioOutput, type CatalogScenarioVersionOutput } from '../../../../utils/api-types';
import { type ScenarioFormInput } from '../ScenarioForm';

// The offered version of a catalogue scenario, and its facts as the scenario form shows them.
const useCatalogScenarioVersion = (scenario: CatalogScenarioOutput | null) => {
  const [shown, setShown] = useState<CatalogScenarioVersionOutput | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setShown(null);
    if (!scenario) return undefined;
    let active = true;
    setLoading(true);
    fetchCatalogScenarioVersions(scenario.catalog_scenario_external_id)
      .then((versions) => {
        if (active) setShown(versions.find(version => version.catalog_scenario_version === scenario.catalog_scenario_version) ?? null);
      })
      .catch(() => undefined)
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [scenario]);

  const formValues: ScenarioFormInput | null = scenario && {
    scenario_name: scenario.catalog_scenario_name ?? scenario.catalog_scenario_external_id,
    scenario_description: scenario.catalog_scenario_description ?? '',
    scenario_category: scenario.catalog_scenario_category,
    scenario_main_focus: shown?.catalog_scenario_main_focus,
    scenario_severity: shown?.catalog_scenario_severity as ScenarioFormInput['scenario_severity'],
    scenario_tags: [],
  };

  return {
    shown,
    loading,
    formValues,
  };
};

export default useCatalogScenarioVersion;
