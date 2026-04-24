import { ListItem, ListItemText, Paper, Switch, TextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { makeStyles } from 'tss-react/mui';

import {
  fetchPlatformParameters,
  updatePlatformDarkParameters,
  updatePlatformLightParameters,
  updatePlatformParameters,
  updatePlatformWhitemarkParameters,
} from '../../../../actions/Application';
import { type LoggedHelper } from '../../../../actions/helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import {
  type PlatformSettings,
  type SettingsPlatformWhitemarkUpdateInput,
  type SettingsUpdateInput,
  type ThemeInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ParametersForm from '../../settings/ParametersForm';
import PlatformInfoPanel from '../../settings/PlatformInfoPanel';
import ThemeForm from '../../settings/ThemeForm';
import ToolsPanel from '../../settings/ToolsPanel';

const useStyles = makeStyles()(theme => ({ paper: { padding: theme.spacing(2) } }));

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
      <Breadcrumbs
        style={{ gridColumn: 'span 6' }}
        variant="object"
        elements={[{ label: t('Settings') }, {
          label: t('Parameters'),
          current: true,
        }]}
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
              <ParametersForm
                onSubmit={onUpdate}
                initialValues={{
                  platform_name: settings?.platform_name,
                  platform_theme: settings?.platform_theme,
                  platform_lang: settings?.platform_lang,
                }}
                canNotManage={cannotManagePlatformSettings}
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
              bottomContent={(
                <>
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
          <div>
            <Typography variant="h4">{t('Tools')}</Typography>
            <ToolsPanel settings={settings} />
          </div>
        </div>
      </div>
    </>
  );
};

export default Parameters;
