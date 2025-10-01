import { List, ListItem, ListItemText, Paper, Switch, TextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchPlatformParameters, updatePlatformDarkParameters, updatePlatformLightParameters, updatePlatformParameters, updatePlatformWhitemarkParameters } from '../../../actions/Application';
import { type LoggedHelper } from '../../../actions/helper';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import ItemBoolean from '../../../components/ItemBoolean';
import ItemCopy from '../../../components/ItemCopy';
import { useHelper } from '../../../store';
import { type PlatformSettings, type SettingsPlatformWhitemarkUpdateInput, type SettingsUpdateInput, type ThemeInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ParametersForm from './ParametersForm';
import ThemeForm from './ThemeForm';

const useStyles = makeStyles()(theme => ({
  paper: {
    padding: theme.spacing(2),
    borderRadius: 4,
  },
  paperList: {
    padding: `0 ${theme.spacing(2)} ${theme.spacing(2)} ${theme.spacing(2)}`,
    borderRadius: 4,
  },
  marginBottom: { marginBottom: theme.spacing(3) },
}));

const Parameters = () => {
  const { classes } = useStyles();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);
  const cannotManagePlatformSettings = ability.cannot(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS);

  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const isEnterpriseEditionValid = settings.platform_license?.license_is_validated;
  useDataLoader(() => {
    dispatch(fetchPlatformParameters());
  });

  const initialValuesDark = {
    accent_color: settings.platform_dark_theme?.accent_color ?? '',
    background_color: settings.platform_dark_theme?.background_color ?? '',
    logo_login_url: settings.platform_dark_theme?.logo_login_url ?? '',
    logo_url: settings.platform_dark_theme?.logo_url ?? '',
    logo_url_collapsed: settings.platform_dark_theme?.logo_url_collapsed ?? '',
    navigation_color: settings.platform_dark_theme?.navigation_color ?? '',
    paper_color: settings.platform_dark_theme?.paper_color ?? '',
    primary_color: settings.platform_dark_theme?.primary_color ?? '',
    secondary_color: settings.platform_dark_theme?.secondary_color ?? '',
  };

  const initialValuesLight = {
    accent_color: settings.platform_light_theme?.accent_color ?? '',
    background_color: settings.platform_light_theme?.background_color ?? '',
    logo_login_url: settings.platform_light_theme?.logo_login_url ?? '',
    logo_url: settings.platform_light_theme?.logo_url ?? '',
    logo_url_collapsed: settings.platform_light_theme?.logo_url_collapsed ?? '',
    navigation_color: settings.platform_light_theme?.navigation_color ?? '',
    paper_color: settings.platform_light_theme?.paper_color ?? '',
    primary_color: settings.platform_light_theme?.primary_color ?? '',
    secondary_color: settings.platform_light_theme?.secondary_color ?? '',
  };

  const onUpdate = (data: SettingsUpdateInput) => dispatch(updatePlatformParameters(data));
  const onUpdateLigthParameters = (data: ThemeInput) => dispatch(updatePlatformLightParameters(data));
  const onUpdateDarkParameters = (data: ThemeInput) => dispatch(updatePlatformDarkParameters(data));
  const updatePlatformWhitemark = (data: SettingsPlatformWhitemarkUpdateInput) => dispatch(updatePlatformWhitemarkParameters(data));
  return (
    <>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
      }}
      >
        <Breadcrumbs
          style={{ gridColumn: 'span 6' }}
          variant="object"
          elements={[{ label: t('Settings') }, {
            label: t('Parameters'),
            current: true,
          }]}
        />
      </div>
      <div style={{
        display: 'grid',
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: '1fr 1fr',
      }}
      >
        <Typography variant="h4">{t('Configuration')}</Typography>
        <Typography variant="h4">{t('OpenAEV platform')}</Typography>
        <Paper variant="outlined" className={`${classes.paper} ${classes.marginBottom}`} style={{ minHeight: 340 }}>
          <ParametersForm
            onSubmit={onUpdate}
            initialValues={{
              platform_name: settings?.platform_name,
              platform_theme: settings?.platform_theme,
              platform_lang: settings?.platform_lang,
              platform_home_dashboard: settings?.platform_home_dashboard,
              platform_scenario_dashboard: settings?.platform_scenario_dashboard,
              platform_simulation_dashboard: settings?.platform_simulation_dashboard,
            }}
            canNotManage={cannotManagePlatformSettings}
          />
        </Paper>
        <Paper variant="outlined" style={{ height: 'auto' }} className={`${classes.paperList} ${classes.marginBottom}`}>
          <List>
            <ListItem divider>
              <ListItemText primary={t('Platform identifier')} />
              <pre
                style={{
                  padding: 0,
                  margin: 0,
                }}
                key={settings.platform_id}
              >
                <ItemCopy content={settings.platform_id ?? ''} variant="inLine" />
              </pre>
            </ListItem>
            <ListItem divider>
              <ListItemText primary={t('Version')} />
              <ItemBoolean variant="large" status={null} neutralLabel={settings?.platform_version?.replace('-SNAPSHOT', '')} />
            </ListItem>
            <ListItem divider>
              <ListItemText primary={t('Edition')} />
              <ItemBoolean
                variant="large"
                neutralLabel={
                  isEnterpriseEditionValid
                    ? t('Enterprise')
                    : t('Community')
                }
                status={null}
              />
            </ListItem>
            <ListItem divider>
              <ListItemText
                primary={t('AI Powered')}
              />
              <ItemBoolean
                variant="large"
                label={
                  // eslint-disable-next-line no-nested-ternary
                  !settings.platform_ai_enabled ? t('Disabled') : settings.platform_ai_has_token
                    ? settings.platform_ai_type
                    : `${settings.platform_ai_type} - ${t('Missing token')}`
                }
                status={(settings.platform_ai_enabled) && (settings.platform_ai_has_token)}
                tooltip={settings.platform_ai_has_token ? `${settings.platform_ai_type} - ${settings.platform_ai_model}` : t('The token is missing in your platform configuration, please ask your Filigran representative to provide you with it or with on-premise deployment instructions. Your can open a support ticket to do so.')}
              />
            </ListItem>
            <ListItem divider>
              <TextField fullWidth label={t('Filigran support key')} variant="standard" disabled />
            </ListItem>
            <ListItem divider>
              <ListItemText primary={t('Remove Filigran logos')} />
              <Switch
                disabled={settings.platform_license?.license_is_validated === false || ability.cannot(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS)}
                checked={settings.platform_whitemark === 'true'}
                onChange={(_event, checked) => updatePlatformWhitemark({ platform_whitemark: checked.toString() })}
              />
            </ListItem>
          </List>
        </Paper>
      </div>
      <div style={{
        display: 'grid',
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: '1fr 1fr 1fr',
      }}
      >
        <div>
          <Typography variant="h4">{t('Dark theme')}</Typography>
          <Paper variant="outlined" classes={{ root: classes.paper }}>
            <ThemeForm
              onSubmit={onUpdateDarkParameters}
              initialValues={initialValuesDark}
              canNotManage={cannotManagePlatformSettings}
            />
          </Paper>
        </div>
        <div>
          <Typography variant="h4">{t('Light theme')}</Typography>
          <Paper variant="outlined" classes={{ root: classes.paper }}>
            <ThemeForm
              onSubmit={onUpdateLigthParameters}
              initialValues={initialValuesLight}
              canNotManage={cannotManagePlatformSettings}
            />
          </Paper>
        </div>
        <div style={{
          display: 'grid',
          gridTemplateRows: 'auto 1fr auto 1fr',
        }}
        >
          <Typography variant="h4">{t('Tools')}</Typography>
          <Paper variant="outlined" classes={{ root: classes.paperList }}>
            <List style={{ paddingTop: 0 }}>
              <ListItem divider>
                <ListItemText primary={t('JAVA Virtual Machine')} />
                <ItemBoolean status={null} variant="large" neutralLabel={settings?.java_version} />
              </ListItem>
              <ListItem divider>
                <ListItemText primary={t('PostgreSQL')} />
                <ItemBoolean status={null} variant="large" neutralLabel={settings?.postgre_version} />
              </ListItem>
              <ListItem divider>
                <ListItemText primary={t('RabbitMQ')} />
                <ItemBoolean status={null} variant="large" neutralLabel={settings?.rabbitmq_version} />
              </ListItem>
              {settings.analytics_engine_type
                && (
                  <ListItem divider>
                    <ListItemText primary={t(settings.analytics_engine_type)} />
                    <ItemBoolean status={null} variant="large" neutralLabel={settings?.analytics_engine_version} />
                  </ListItem>
                )}
              <ListItem divider>
                <ListItemText primary={t('Telemetry manager')} />
                <ItemBoolean status={settings?.telemetry_manager_enable} variant="large" label={settings?.telemetry_manager_enable ? t('Enable') : t('Disabled')} />
              </ListItem>
            </List>
          </Paper>
        </div>
      </div>
    </>
  );
};

export default Parameters;
