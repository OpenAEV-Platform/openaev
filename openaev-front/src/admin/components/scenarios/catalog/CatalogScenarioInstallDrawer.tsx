import { type FunctionComponent } from 'react';

import { fetchCatalogScenarioInstallPreview, installCatalogScenario } from '../../../../actions/catalog/scenario-catalog-actions';
import { useFormatter } from '../../../../components/i18n';
import ScenarioTargetsDrawer from './ScenarioTargetsDrawer';

interface Props {
  externalId: string;
  name: string;
  version: string;
  onClose: () => void;
  onInstalled: (scenarioId: string) => void;
}

// Injects of the installed version keep their targets; the others get the chosen ones.
const CatalogScenarioInstallDrawer: FunctionComponent<Props> = ({ externalId, name, version, onClose, onInstalled }) => {
  const { t } = useFormatter();
  return (
    <ScenarioTargetsDrawer
      title={`${t('Install')} ${name} v${version}`}
      mode="install"
      load={() => fetchCatalogScenarioInstallPreview(externalId, version).then(({ data }) => ({
        injects: data.injects,
        previousVersion: data.previous_version,
        missingRequirements: data.missing_requirements,
      }))}
      onSubmit={input => installCatalogScenario(externalId, version, input).then(({ data }) => onInstalled(data.scenario_id))}
      onClose={onClose}
    />
  );
};

export default CatalogScenarioInstallDrawer;
