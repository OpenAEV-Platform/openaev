import { List, ListItem, ListItemText, Paper, Switch, TextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import {
  fetchPlatformParameters,
  updatePlatformDarkParameters,
  updatePlatformLightParameters,
  updatePlatformParameters,
  updatePlatformWhitemarkParameters,
} from '../../../actions/Application';
import { type LoggedHelper } from '../../../actions/helper';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import ItemBoolean from '../../../components/ItemBoolean';
import { useHelper } from '../../../store';
import {
  type PlatformSettings,
  type SettingsPlatformWhitemarkUpdateInput,
  type SettingsUpdateInput,
  type ThemeInput,
} from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ParametersForm from './ParametersForm';
import PlatformInfoPanel from './PlatformInfoPanel';
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
        marginBottom: theme.spacing(3),
      }}
      >
        <Typography variant="h4">{t('Configuration')}</Typography>
        <Typography variant="h4">{t('OpenAEV platform')}</Typography>
        <Paper variant="outlined" className={`${classes.paper}`}>
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
        <PlatformInfoPanel settings={settings}>
          <ListItem divider>
            <TextField fullWidth label={t('Filigran support key')} variant="standard" disabled />
          </ListItem>
          <ListItem>
            <ListItemText primary={t('Remove Filigran logos')} />
            <Switch
              disabled={settings.platform_license?.license_is_validated === false || ability.cannot(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS)}
              checked={settings.platform_whitemark === 'true'}
              onChange={(_event, checked) => updatePlatformWhitemark({ platform_whitemark: checked.toString() })}
            />
          </ListItem>
        </PlatformInfoPanel>
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
              <ListItem divider>
                <ListItemText primary={t('SMTP')} />
                <ItemBoolean status={settings?.smtp_service_available === 'true'} variant="large" label={settings?.smtp_service_available === 'true' ? t('Enable') : t('Disabled')} />
              </ListItem>
              <ListItem divider>
                <ListItemText primary={t('IMAP')} />
                <ItemBoolean status={settings?.imap_service_available === 'true'} variant="large" label={settings?.imap_service_available === 'true' ? t('Enable') : t('Disabled')} />
              </ListItem>
            </List>
          </Paper>
        </div>
      </div>
    </>
  );
};

export default Parameters;
