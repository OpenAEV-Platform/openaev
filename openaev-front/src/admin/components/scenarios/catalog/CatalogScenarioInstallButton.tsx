import { Button } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';

interface Props {
  scenario: CatalogScenarioOutput;
  canInstall: boolean;
  onInstall: () => void;
}

// A scenario missing a requirement cannot be installed.
const CatalogScenarioInstallButton: FunctionComponent<Props> = ({ scenario, canInstall, onInstall }) => {
  const { t } = useFormatter();
  if (!canInstall) {
    return null;
  }
  return <Button size="sm" disabled={scenario.catalog_scenario_missing_requirements.length > 0} onClick={onInstall}>{t('Install')}</Button>;
};

export default CatalogScenarioInstallButton;
