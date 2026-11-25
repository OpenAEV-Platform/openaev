import { Button } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { installedScenarioUrl } from './catalogScenarioInstallation';

interface Props {
  scenario: CatalogScenarioOutput;
  canInstall: boolean;
  onInstall: () => void;
  // Installs the scenario, then launches it.
  onLaunch?: () => void;
  size?: 'sm';
  // Updates the installed scenario to this version; only when it has one that can be applied.
  onUpdate?: () => void;
}

// Opens the scenario installed from the item, else installs it, and launches it when allowed.
const CatalogScenarioAction: FunctionComponent<Props> = ({ scenario, canInstall, onInstall, onLaunch, size, onUpdate }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const installedUrl = installedScenarioUrl(scenario);
  if (installedUrl) {
    const open = (
      <Button asChild size={size} priority="secondary">
        <Link to={installedUrl} onClick={event => event.stopPropagation()}>{t('Open')}</Link>
      </Button>
    );
    if (!onUpdate) {
      return open;
    }
    return (
      <div style={{
        display: 'flex',
        gap: theme.spacing(1),
      }}
      >
        {open}
        <Button
          size={size}
          onClick={(event) => {
            event.stopPropagation();
            onUpdate();
          }}
        >
          {t('Update')}
        </Button>
      </div>
    );
  }
  if (!canInstall) {
    return null;
  }
  if (!onLaunch) {
    return <Button size={size} onClick={onInstall}>{t('Install')}</Button>;
  }
  return (
    <div style={{
      display: 'flex',
      gap: theme.spacing(1),
    }}
    >
      <Button size={size} priority="secondary" onClick={onInstall}>{t('Install')}</Button>
      <Button size={size} onClick={onLaunch}>{t('Launch')}</Button>
    </div>
  );
};

export default CatalogScenarioAction;
