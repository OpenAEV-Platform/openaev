import { Button } from '@filigran/design-system';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { installedScenarioUrl } from './catalogScenarioInstallation';

interface Props {
  scenario: CatalogScenarioOutput;
  canInstall: boolean;
  onInstall: () => void;
  size?: 'sm';
}

// Opens the scenario installed from the item, else installs it; one missing a requirement cannot be installed.
const CatalogScenarioAction: FunctionComponent<Props> = ({ scenario, canInstall, onInstall, size }) => {
  const { t } = useFormatter();
  const installedUrl = installedScenarioUrl(scenario);
  if (installedUrl) {
    return (
      <Button asChild size={size} priority="secondary">
        <Link to={installedUrl} onClick={event => event.stopPropagation()}>{t('Open')}</Link>
      </Button>
    );
  }
  if (!canInstall) {
    return null;
  }
  return <Button size={size} disabled={scenario.catalog_scenario_missing_requirements.length > 0} onClick={onInstall}>{t('Install')}</Button>;
};

export default CatalogScenarioAction;
