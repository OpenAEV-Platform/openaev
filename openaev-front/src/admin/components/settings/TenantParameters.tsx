import { ListItem, ListItemText, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';

import { fetchPlatformParameters } from '../../../actions/Application';
import type { LoggedHelper } from '../../../actions/helper';
import {
  fetchTenantSettings,
  updateTenantSettings,
} from '../../../actions/settings/tenant-settings-action';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import ItemCopy from '../../../components/ItemCopy';
import { useHelper } from '../../../store';
import type { PlatformSettings, TenantSettingsOutput, TenantSettingsUpdateInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { TENANT_SETTINGS_LABEL } from '../nav/config/settings.config';
import PlatformInfoPanel from './PlatformInfoPanel';
import TenantParametersForm from './TenantParametersForm';
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

  return (
    <>
      <Breadcrumbs
        variant="object"
        elements={[
          { label: t(TENANT_SETTINGS_LABEL) },
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
            <Typography variant="h4">{t('Default dashboards')}</Typography>
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
            <Typography variant="h4">{t('Tools')}</Typography>
            <ToolsPanel settings={settings} />
          </div>
        </div>
      </div>
    </>
  );
};

export default TenantParameters;
