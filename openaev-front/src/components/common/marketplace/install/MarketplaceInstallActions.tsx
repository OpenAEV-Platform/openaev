import { Button, IconButton, Switch, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { HelpOutlineOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type ReactNode, useEffect, useState } from 'react';

import { fetchCatalogSettings, updateCatalogSettings } from '../../../../actions/catalog/payload-catalog-actions';
import { type CatalogInstallAllOutput, type CatalogSettingsOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { useFormatter } from '../../../i18n';

interface Props {
  autoInstallSetting: keyof CatalogSettingsOutput;
  // Translated by the caller, as the message.
  autoInstallHelp: string;
  canInstall: boolean;
  installAll: () => Promise<CatalogInstallAllOutput>;
  installedMessage: (count: number) => ReactNode;
  onInstalledAll: () => void;
}

// What acts on the whole catalogue of an Available tab: the auto-install switch and Install all.
const MarketplaceInstallActions = ({ autoInstallSetting, autoInstallHelp, canInstall, installAll, installedMessage, onInstalledAll }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const canChangeSettings = useAbility().can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const [settings, setSettings] = useState<CatalogSettingsOutput | null>(null);
  const [installingAll, setInstallingAll] = useState(false);

  useEffect(() => {
    fetchCatalogSettings().then(setSettings).catch(() => undefined);
  }, []);

  const changeAutoInstall = (enabled: boolean) => settings && updateCatalogSettings({
    ...settings,
    [autoInstallSetting]: enabled,
  }).then(setSettings).catch(() => undefined);
  // The server installs every item never installed, on every page.
  const installEverything = () => {
    setInstallingAll(true);
    installAll()
      .then(({ installed_ids }) => {
        MESSAGING$.notifySuccess(installedMessage(installed_ids?.length ?? 0));
        onInstalledAll();
      })
      .catch(() => undefined)
      .finally(() => setInstallingAll(false));
  };

  return (
    <>
      {canChangeSettings && settings && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(0.5),
        }}
        >
          <Switch label={t('Auto-install')} checked={settings[autoInstallSetting]} onCheckedChange={changeAutoInstall} />
          <Tooltip>
            <TooltipTrigger asChild>
              <IconButton icon={<HelpOutlineOutlined fontSize="small" />} aria-label={autoInstallHelp} priority="tertiary" size="sm" />
            </TooltipTrigger>
            <TooltipContent>{autoInstallHelp}</TooltipContent>
          </Tooltip>
        </div>
      )}
      {canInstall && <Button priority="secondary" onClick={installEverything} loading={installingAll}>{t('Install all')}</Button>}
    </>
  );
};

export default MarketplaceInstallActions;
