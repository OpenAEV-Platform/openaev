import { ListItem, ListItemText, Paper, Switch, TextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';

import { fetchPlatformParameters, updatePlatformWhitemarkParameters } from '../../../actions/Application';
import type { LoggedHelper } from '../../../actions/helper';
import {
  fetchTenantSettings,
  updateTenantDarkTheme,
  updateTenantLightTheme,
  updateTenantSettings,
} from '../../../actions/settings/tenant-settings-action';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import ItemCopy from '../../../components/ItemCopy';
import { useHelper } from '../../../store';
import type { PlatformSettings, SettingsPlatformWhitemarkUpdateInput, TenantSettingsOutput, TenantSettingsUpdateInput, ThemeInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import EEChip from '../common/entreprise_edition/EEChip';
import { SETTINGS_LABEL } from '../nav/config/settings.config';
import PlatformInfoPanel from './PlatformInfoPanel';
import TenantParametersForm from './TenantParametersForm';
import ThemeForm from './ThemeForm';
import ToolsPanel from './ToolsPanel';

const TenantParameters = () => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);
  const cannotManage = ability.cannot(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const { currentUserTenant } = useAuth();

  const { tenantSettings, settings }: {
    tenantSettings: TenantSettingsOutput;
    settings: PlatformSettings;
  } = useHelper((helper: LoggedHelper) => ({
    tenantSettings: helper.getTenantSettings(),
    settings: helper.getPlatformSettings(),
  }));

  useDataLoader(() => {
    dispatch(fetchPlatformParameters());
    dispatch(fetchTenantSettings());
  });

  const onSubmit = async (data: TenantSettingsUpdateInput) => {
    await updateTenantSettings(data);
    dispatch(fetchTenantSettings());
  };

  const updatePlatformWhitemark = (data: SettingsPlatformWhitemarkUpdateInput) => dispatch(updatePlatformWhitemarkParameters(data));

  const onUpdateDarkTheme = async (data: ThemeInput) => {
    await updateTenantDarkTheme(data);
    dispatch(fetchTenantSettings());
  };
  const onUpdateLightTheme = async (data: ThemeInput) => {
    await updateTenantLightTheme(data);
    dispatch(fetchTenantSettings());
  };

  const initialValuesDark = {
    accent_color: tenantSettings.platform_dark_theme?.accent_color ?? '',
    background_color: tenantSettings.platform_dark_theme?.background_color ?? '',
    logo_login_url: tenantSettings.platform_dark_theme?.logo_login_url ?? '',
    logo_url: tenantSettings.platform_dark_theme?.logo_url ?? '',
    logo_url_collapsed: tenantSettings.platform_dark_theme?.logo_url_collapsed ?? '',
    navigation_color: tenantSettings.platform_dark_theme?.navigation_color ?? '',
    paper_color: tenantSettings.platform_dark_theme?.paper_color ?? '',
    primary_color: tenantSettings.platform_dark_theme?.primary_color ?? '',
    secondary_color: tenantSettings.platform_dark_theme?.secondary_color ?? '',
  };

  const initialValuesLight = {
    accent_color: tenantSettings.platform_light_theme?.accent_color ?? '',
    background_color: tenantSettings.platform_light_theme?.background_color ?? '',
    logo_login_url: tenantSettings.platform_light_theme?.logo_login_url ?? '',
    logo_url: tenantSettings.platform_light_theme?.logo_url ?? '',
    logo_url_collapsed: tenantSettings.platform_light_theme?.logo_url_collapsed ?? '',
    navigation_color: tenantSettings.platform_light_theme?.navigation_color ?? '',
    paper_color: tenantSettings.platform_light_theme?.paper_color ?? '',
    primary_color: tenantSettings.platform_light_theme?.primary_color ?? '',
    secondary_color: tenantSettings.platform_light_theme?.secondary_color ?? '',
  };

  return (
    <>
      <Breadcrumbs
        variant="object"
        elements={[
          { label: t(SETTINGS_LABEL) },
          {
            label: t('Parameters'),
            current: true,
          },
        ]}
      />
      <div style={{
        display: 'grid',
        gap: `${theme.spacing(3)} 0`,
      }}
      >
        <div style={{
          display: 'grid',
          gap: `0px ${theme.spacing(3)}`,
          gridTemplateColumns: '1fr 1fr',
        }}
        >
          <div style={{
            display: 'flex',
            flexDirection: 'column',
          }}
          >
            <Typography variant="h4">{t('Configuration')}</Typography>
            <Paper
              variant="outlined"
              style={{
                padding: theme.spacing(2),
                flex: 1,
              }}
            >
              <TenantParametersForm
                onSubmit={onSubmit}
                initialValues={{
                  platform_name: tenantSettings.platform_name ?? '',
                  platform_theme: tenantSettings.platform_theme ?? '',
                  platform_lang: tenantSettings.platform_lang ?? '',
                  platform_home_dashboard: tenantSettings.platform_home_dashboard ?? '',
                  platform_scenario_dashboard: tenantSettings.platform_scenario_dashboard ?? '',
                  platform_simulation_dashboard: tenantSettings.platform_simulation_dashboard ?? '',
                }}
                canNotManage={cannotManage}
              />
            </Paper>
          </div>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
          }}
          >
            <Typography variant="h4">{t('OpenAEV platform')}</Typography>
            <PlatformInfoPanel
              settings={settings}
              topContent={(
                <ListItem divider>
                  <ListItemText primary={t('Tenant identifier')} />
                  <pre style={{
                    padding: 0,
                    margin: 0,
                  }}
                  >
                    <ItemCopy content={currentUserTenant?.tenant_id ?? ''} variant="inLine" />
                  </pre>
                </ListItem>
              )}
              bottomContent={(
                <>
                  <ListItem divider>
                    <TextField fullWidth label={t('Filigran support key')} variant="standard" disabled />
                  </ListItem>
                  <ListItem>
                    <ListItemText primary={(
                      <span style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                      >
                        {t('Remove Filigran logos')}
                        {settings.platform_license?.license_is_validated !== true && <EEChip clickable featureDetectedInfo="WHITEMARK" />}
                      </span>
                    )}
                    />
                    <Switch
                      disabled={settings.platform_license?.license_is_validated === false || ability.cannot(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS)}
                      checked={settings.platform_whitemark === 'true'}
                      onChange={(_event, checked) => updatePlatformWhitemark({ platform_whitemark: checked.toString() })}
                    />
                  </ListItem>
                </>
              )}
            />
          </div>
        </div>
        <div style={{
          display: 'grid',
          gap: `0px ${theme.spacing(3)}`,
          gridTemplateColumns: '1fr 1fr 1fr',
        }}
        >
          <div>
            <Typography variant="h4">{t('Dark theme')}</Typography>
            <Paper variant="outlined" style={{ padding: theme.spacing(2) }}>
              <ThemeForm
                onSubmit={onUpdateDarkTheme}
                initialValues={initialValuesDark}
                canNotManage={cannotManage}
              />
            </Paper>
          </div>
          <div>
            <Typography variant="h4">{t('Light theme')}</Typography>
            <Paper variant="outlined" style={{ padding: theme.spacing(2) }}>
              <ThemeForm
                onSubmit={onUpdateLightTheme}
                initialValues={initialValuesLight}
                canNotManage={cannotManage}
              />
            </Paper>
          </div>
          <div>
            <Typography variant="h4">{t('Tools')}</Typography>
            <ToolsPanel settings={settings} />
          </div>
        </div>
        <div style={{ marginBottom: theme.spacing(1) }} />
      </div>
    </>
  );
};

export default TenantParameters;
