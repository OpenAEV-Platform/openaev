import { type FunctionComponent } from 'react';

import { installCatalogScenario } from '../../../../actions/catalog/scenario-catalog-actions';
import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { SCENARIO_TYPE_CHAINED } from '../scenario/ScenarioType';
import { type ScenarioTargets, toTargetsInput } from '../targets/scenarioTargets';
import ScenarioTargetsDrawer from '../targets/ScenarioTargetsDrawer';
import { catalogScenarioType } from './catalogScenarioFilters';

interface Props {
  scenario: CatalogScenarioOutput;
  onClose: () => void;
  onInstalled: (scenario: CatalogScenarioOutput) => void;
}

const CatalogScenarioInstallDrawer: FunctionComponent<Props> = ({ scenario, onClose, onInstalled }) => {
  const { t } = useFormatter();
  const chained = catalogScenarioType(scenario) === SCENARIO_TYPE_CHAINED;

  const install = (targets: ScenarioTargets) => installCatalogScenario(scenario, chained
    ? { scenario_type: 'chained' }
    : {
        scenario_type: 'time-based',
        ...toTargetsInput(targets),
      }).then(() => onInstalled(scenario));

  return (
    <ScenarioTargetsDrawer
      title={`${t('Install')} ${scenario.catalog_scenario_name ?? scenario.catalog_scenario_external_id} v${scenario.catalog_scenario_version}`}
      description={chained
        ? t('A chained scenario acts only inside its scope: set it on the scenario once installed.')
        : t('Choose the targets of the scenario. You can change them later on the scenario.')}
      withTargets={!chained}
      submitLabel={t('Install')}
      onSubmit={install}
      onClose={onClose}
    />
  );
};

export default CatalogScenarioInstallDrawer;
