import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, List, ListItem, ListItemText, Paper, Switch, TextField, Typography } from '@mui/material';
import { useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import {
  fetchPlatformParameters,
  updatePlatformDarkParameters,
  updatePlatformEnterpriseEditionParameters,
  updatePlatformLightParameters,
  updatePlatformParameters,
  updatePlatformWhitemarkParameters,
} from '../../../actions/Application';
import { type LoggedHelper } from '../../../actions/helper';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import ItemBoolean from '../../../components/ItemBoolean';
import ItemCopy from '../../../components/ItemCopy';
import { useHelper } from '../../../store';
import { type PlatformSettings, type SettingsEnterpriseEditionUpdateInput, type SettingsPlatformWhitemarkUpdateInput, type SettingsUpdateInput, type ThemeInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import EnterpriseEditionButton from '../common/entreprise_edition/EnterpriseEditionButton';
import ParametersForm from './ParametersForm';
import ThemeForm from './ThemeForm';

const useStyles = makeStyles()(theme => ({
  parameterPage: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr 1fr 1fr 1fr 1fr',
    columnGap: theme.spacing(3),
  },
  paper: {
    padding: theme.spacing(2),
    borderRadius: 4,
  },
  paperList: {
    padding: `0 ${theme.spacing(2)} ${theme.spacing(2)} ${theme.spacing(2)}`,
    borderRadius: 4,
  },
  button: { marginBottom: theme.spacing(1) },
  marginBottom: { marginBottom: theme.spacing(3) },
}));

const Parameters = () => {
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { t, fldt } = useFormatter();
  const [openEEChanges, setOpenEEChanges] = useState(false);
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const isEnterpriseEditionActivated = settings.platform_license?.license_is_enterprise;
  const isEnterpriseEditionByConfig = settings.platform_license?.license_is_by_configuration;
  const isEnterpriseEditionValid = settings.platform_license?.license_is_validated;
  const isEnterpriseEdition = settings.platform_license?.license_is_validated === true;
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
  const updateEnterpriseEdition = (data: SettingsEnterpriseEditionUpdateInput) => dispatch(updatePlatformEnterpriseEditionParameters(data));
  const updatePlatformWhitemark = (data: SettingsPlatformWhitemarkUpdateInput) => dispatch(updatePlatformWhitemarkParameters(data));
  return (
    <div className={classes.parameterPage}>
      <Breadcrumbs
        style={{ gridColumn: 'span 6' }}
        variant="object"
        elements={[{ label: t('Settings') }, {
          label: t('Parameters'),
          current: true,
        }]}
      />
      {isEnterpriseEditionActivated && (
        <>
          <Typography
            style={{
              gridColumn: 'span 3',
              alignSelf: 'end',
            }}
            variant="h4"
            gutterBottom
          >
            {t('Enterprise Edition')}
          </Typography>
          <Typography
            style={{
              gridColumn: 'span 1',
              alignSelf: 'end',
            }}
            variant="h4"
            gutterBottom
          >
            {t('License')}
          </Typography>
          {!isEnterpriseEditionByConfig && !isEnterpriseEdition && (
            <EnterpriseEditionButton
              style={{
                marginLeft: 'auto',
                gridColumn: 'span 2',
              }}
              classes={{ root: classes.button }}
            />
          )}
          {!isEnterpriseEditionByConfig && isEnterpriseEdition && (
            <>
              <Button
                size="small"
                variant="outlined"
                color="primary"
                style={{
                  marginLeft: 'auto',
                  gridColumn: 'span 2',
                }}
                onClick={() => setOpenEEChanges(true)}
                classes={{ root: classes.button }}
              >
                {t('Disable Enterprise Edition')}
              </Button>
              <Dialog
                slotProps={{ paper: { elevation: 1 } }}
                open={openEEChanges}
                keepMounted
                onClose={() => setOpenEEChanges(false)}
              >
                <DialogTitle>{t('Disable Enterprise Edition')}</DialogTitle>
                <DialogContent>
                  <DialogContentText>
                    <Alert
                      severity="warning"
                      variant="outlined"
                      color="error"
                    >
                      {t('You are about to disable the "Enterprise Edition" mode. Please note that this action will disable access to certain advanced features.')}
                      <br />
                      <br />
                      <strong>{t('However, your existing data will remain intact and will not be lost.')}</strong>
                    </Alert>
                  </DialogContentText>
                </DialogContent>
                <DialogActions>
                  <Button
                    onClick={() => {
                      setOpenEEChanges(false);
                    }}
                  >
                    {t('Cancel')}
                  </Button>
                  <Button
                    color="secondary"
                    onClick={() => {
                      setOpenEEChanges(false);
                      updateEnterpriseEdition({ platform_enterprise_license: '' });
                    }}
                  >
                    {t('Validate')}
                  </Button>
                </DialogActions>
              </Dialog>
            </>
          )}

          <Paper style={{ gridColumn: 'span 3' }} className={`${classes.paperList} ${classes.marginBottom}`} variant="outlined">
            <List style={{ padding: 0 }}>
              <ListItem divider={true}>
                <ListItemText primary={t('Organization')} />
                <ItemBoolean
                  variant="xlarge"
                  neutralLabel={settings.platform_license?.license_customer}
                  status={null}
                />
              </ListItem>
              <ListItem divider={true}>
                <ListItemText primary={t('Creator')} />
                <ItemBoolean
                  variant="xlarge"
                  neutralLabel={settings.platform_license?.license_creator}
                  status={null}
                />
              </ListItem>
              <ListItem divider={true}>
                <ListItemText primary={t('Scope')} />
                <ItemBoolean
                  variant="xlarge"
                  neutralLabel={settings.platform_license?.license_is_global ? t('Global') : t('Current instance')}
                  status={null}
                />
              </ListItem>
            </List>
          </Paper>
          <Paper style={{ gridColumn: 'span 3' }} className={`${classes.paperList} ${classes.marginBottom}`} variant="outlined">
            <List style={{ padding: 0 }}>
              {!settings.platform_license?.license_is_expired && settings.platform_license?.license_is_prevention && (
                <ListItem divider={false}>
                  <Alert severity="warning" variant="outlined" style={{ width: '100%' }}>
                    {t('Your Enterprise Edition license will expire in less than 3 months.')}
                  </Alert>
                </ListItem>
              )}
              {!settings.platform_license?.license_is_validated && settings.platform_license?.license_is_valid_cert && (
                <ListItem divider={false}>
                  <Alert severity="error" variant="outlined" style={{ width: '100%' }}>
                    {t('Your Enterprise Edition license is expired. Please contact your Filigran representative.')}
                  </Alert>
                </ListItem>
              )}
              <ListItem divider={true}>
                <ListItemText primary={t('Start date')} />
                <ItemBoolean
                  variant="xlarge"
                  label={fldt(settings.platform_license?.license_start_date)}
                  status={!settings.platform_license?.license_is_expired}
                />
              </ListItem>
              <ListItem divider={true}>
                <ListItemText primary={t('Expiration date')} />
                <ItemBoolean
                  variant="xlarge"
                  label={fldt(settings.platform_license?.license_expiration_date)}
                  status={!settings.platform_license?.license_is_expired}
                />
              </ListItem>
              <ListItem divider={!settings.platform_license?.license_is_prevention}>
                <ListItemText primary={t('License type')} />
                <ItemBoolean
                  variant="large"
                  neutralLabel={settings.platform_license?.license_type}
                  status={null}
                />
              </ListItem>
            </List>
          </Paper>
        </>
      )}

      <Typography
        style={{
          gridColumn: 'span 3',
          alignSelf: 'end',
        }}
        gutterBottom
        variant="h4"
      >
        {t('Configuration')}
      </Typography>
      <Typography
        style={{
          gridColumn: 'span 1',
          alignSelf: 'end',
        }}
        gutterBottom
        variant="h4"
      >
        {t('OpenBAS platform')}
      </Typography>
      {!isEnterpriseEditionActivated && (
        <EnterpriseEditionButton
          style={{
            marginLeft: 'auto',
            gridColumn: 'span 2',
          }}
          classes={{ root: classes.button }}
        />
      )}

      <Paper sx={{ gridColumn: 'span 3' }} variant="outlined" className={`${classes.paper} ${classes.marginBottom}`} style={{ minHeight: 340 }}>
        <ParametersForm
          onSubmit={onUpdate}
          initialValues={{
            platform_name: settings?.platform_name,
            platform_theme: settings?.platform_theme,
            platform_lang: settings?.platform_lang,
          }}
        />
      </Paper>
      <Paper variant="outlined" className={`${classes.paperList} ${classes.marginBottom}`} sx={{ gridColumn: 'span 3' }}>
        <List>
          <ListItem divider={true}>
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
          <ListItem divider={true}>
            <ListItemText primary={t('Version')} />
            <ItemBoolean variant="large" status={null} neutralLabel={settings?.platform_version?.replace('-SNAPSHOT', '')} />
          </ListItem>
          <ListItem divider={true}>
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
          <ListItem divider={true}>
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
          <ListItem divider={true}>
            <TextField fullWidth={true} label={t('Filigran support key')} variant="standard" disabled={true} />
          </ListItem>
          <ListItem divider={true}>
            <ListItemText primary={t('Remove Filigran logos')} />
            <Switch
              disabled={settings.platform_license?.license_is_validated === false}
              checked={settings.platform_whitemark === 'true'}
              onChange={(_event, checked) => updatePlatformWhitemark({ platform_whitemark: checked.toString() })}
            />
          </ListItem>
        </List>
      </Paper>

      <Typography style={{ gridColumn: 'span 2' }} gutterBottom variant="h4">{t('Dark theme')}</Typography>
      <Typography style={{ gridColumn: 'span 2' }} gutterBottom variant="h4">{t('Light theme')}</Typography>
      <Typography style={{ gridColumn: 'span 2' }} gutterBottom variant="h4">{t('Tools')}</Typography>
      <Paper variant="outlined" classes={{ root: classes.paper }} sx={{ gridColumn: 'span 2' }}>
        <ThemeForm onSubmit={onUpdateDarkParameters} initialValues={initialValuesDark} />
      </Paper>
      <Paper variant="outlined" classes={{ root: classes.paper }} sx={{ gridColumn: 'span 2' }}>
        <ThemeForm onSubmit={onUpdateLigthParameters} initialValues={initialValuesLight} />
      </Paper>
      <Paper variant="outlined" classes={{ root: classes.paperList }} sx={{ gridColumn: 'span 2' }}>
        <List style={{ paddingTop: 0 }}>
          <ListItem divider={true}>
            <ListItemText primary={t('JAVA Virtual Machine')} />
            <ItemBoolean status={null} variant="large" neutralLabel={settings?.java_version} />
          </ListItem>
          <ListItem divider={true}>
            <ListItemText primary={t('PostgreSQL')} />
            <ItemBoolean status={null} variant="large" neutralLabel={settings?.postgre_version} />
          </ListItem>
          <ListItem divider={true}>
            <ListItemText primary={t('RabbitMQ')} />
            <ItemBoolean status={null} variant="large" neutralLabel={settings?.rabbitmq_version} />
          </ListItem>
          <ListItem divider={true}>
            <ListItemText primary={t('Telemetry manager')} />
            <ItemBoolean status={settings?.telemetry_manager_enable} variant="large" label={settings?.telemetry_manager_enable ? t('Enable') : t('Disabled')} />
          </ListItem>
        </List>
      </Paper>
    </div>

  );
};

export default Parameters;
