import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, List, ListItem, ListItemText, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';
import { useState } from 'react';

import { updatePlatformEnterpriseEditionParameters } from '../../../../actions/Application';
import type { LoggedHelper } from '../../../../actions/helper';
import { useFormatter } from '../../../../components/i18n';
import ItemBoolean from '../../../../components/ItemBoolean';
import { useHelper } from '../../../../store';
import type { PlatformSettings, SettingsEnterpriseEditionUpdateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import EnterpriseEditionButton from '../../common/entreprise_edition/EnterpriseEditionButton';

const EnterpriseEditionSettings: React.FC = () => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { t, fldt } = useFormatter();
  const [openEEChanges, setOpenEEChanges] = useState(false);
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const isEnterpriseEditionActivated = settings.platform_license?.license_is_enterprise;
  const isEnterpriseEditionByConfig = settings.platform_license?.license_is_by_configuration;
  const isEnterpriseEdition = settings.platform_license?.license_is_validated === true;
  const updateEnterpriseEdition = (data: SettingsEnterpriseEditionUpdateInput) => dispatch(updatePlatformEnterpriseEditionParameters(data));

  return (
    <>
      {isEnterpriseEditionActivated && (
        <Grid container flexDirection="column" gap="0" size={6}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            marginBottom: theme.spacing(0.5),
          }}
          >
            <Typography
              variant="h4"
              gutterBottom
              style={{
                display: 'flex',
                alignItems: 'flex-end',
                marginBottom: 0,
                minHeight: theme.spacing(4.5),
              }}
            >
              {t('Enterprise Edition')}
            </Typography>
            {!isEnterpriseEditionByConfig && !isEnterpriseEdition && (
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
                <EnterpriseEditionButton />
              </Can>
            )}
            {!isEnterpriseEditionByConfig && isEnterpriseEdition && (
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
                <Button
                  size="small"
                  variant="outlined"
                  color="primary"
                  onClick={() => setOpenEEChanges(true)}
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
                    <Alert
                      severity="warning"
                      variant="outlined"
                      color="error"
                    >
                      <Typography>{t('You are about to disable the "Enterprise Edition" mode. Please note that this action will disable access to certain advanced features.')}</Typography>
                      <Typography
                        style={{ marginTop: theme.spacing(6) }}
                        fontWeight="bold"
                      >
                        {t('However, your existing data will remain intact and will not be lost.')}
                      </Typography>
                    </Alert>
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
              </Can>
            )}
          </div>

          <Paper
            style={{
              padding: theme.spacing(0, 2, 2),
              borderRadius: 4,
              flexGrow: 1,
            }}
            variant="outlined"
          >
            <List style={{ padding: 0 }}>
              <ListItem divider>
                <ListItemText primary={t('Organization')} />
                <ItemBoolean
                  variant="xlarge"
                  neutralLabel={settings.platform_license?.license_customer}
                  status={null}
                />
              </ListItem>

              <ListItem divider>
                <ListItemText primary={t('Scope')} />
                <ItemBoolean
                  variant="xlarge"
                  neutralLabel={settings.platform_license?.license_is_global ? t('Global') : t('Current instance')}
                  status={null}
                />
              </ListItem>
              {!settings.platform_license?.license_is_expired && settings.platform_license?.license_is_prevention && (
                <ListItem>
                  <Alert severity="warning" variant="outlined" style={{ width: '100%' }}>
                    {t('Your Enterprise Edition license will expire in less than 3 months.')}
                  </Alert>
                </ListItem>
              )}
              {!settings.platform_license?.license_is_validated && settings.platform_license?.license_is_valid_cert && (
                <ListItem>
                  <Alert severity="error" variant="outlined" style={{ width: '100%' }}>
                    {t('Your Enterprise Edition license is expired. Please contact your Filigran representative.')}
                  </Alert>
                </ListItem>
              )}
              <ListItem divider>
                <ListItemText primary={t('Start date')} />
                <ItemBoolean
                  variant="xlarge"
                  label={fldt(settings.platform_license?.license_start_date)}
                  status={!settings.platform_license?.license_is_expired}
                />
              </ListItem>
              <ListItem divider>
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
                  variant="xlarge"
                  neutralLabel={settings.platform_license?.license_type}
                  status={null}
                />
              </ListItem>
            </List>
          </Paper>
        </Grid>
      )}

      {!isEnterpriseEditionActivated && (
        <Grid container flexDirection="column" gap="0" size={6}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            marginBottom: theme.spacing(0.5),
          }}
          >
            <Typography
              variant="h4"
              gutterBottom
              style={{
                display: 'flex',
                alignItems: 'flex-end',
                marginBottom: 0,
                minHeight: theme.spacing(4.5),
              }}
            >
              {t('Enterprise Edition')}
            </Typography>
            {!isEnterpriseEditionActivated && (
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
                <EnterpriseEditionButton />
              </Can>
            )}
          </div>

          <Paper
            style={{
              padding: theme.spacing(2),
              borderRadius: 4,
              flexGrow: 1,
            }}
            className="paper-for-grid"
            variant="outlined"
          >
            <Typography variant="h6" style={{ marginBottom: theme.spacing(2) }}>
              {t('Enable powerful features with OpenAEV Enterprise Edition')}
            </Typography>
            <Typography style={{ marginBottom: theme.spacing(2) }}>{t('OpenAEV Enterprise Edition (EE) provides highly demanding organizations with a version that includes additional and powerful features, which require specific investments in research and development.')}</Typography>
            <Typography>{t('By taking an Enterprise Edition license, you will be able to use:')}</Typography>
            <List
              dense
              style={{
                listStyle: 'disc',
                marginLeft: theme.spacing(2),
              }}
            >
              <ListItem style={{
                display: 'list-item',
                paddingLeft: 0,
                marginLeft: theme.spacing(2),
              }}
              >
                {t('Generative AI (content generation including emails, media pressure articles, scenarios)')}
              </ListItem>
              <ListItem style={{
                display: 'list-item',
                paddingLeft: 0,
                marginLeft: theme.spacing(2),
              }}
              >
                {t('CrowdStrike Falcon and Tanium Agent')}
              </ListItem>
              <ListItem style={{
                display: 'list-item',
                paddingLeft: 0,
                marginLeft: theme.spacing(2),
              }}
              >
                {t('Remediations in Vulnerabilities')}
              </ListItem>
              <ListItem style={{
                display: 'list-item',
                paddingLeft: 0,
                marginLeft: theme.spacing(2),
              }}
              >
                {t('And many more features...')}
              </ListItem>
            </List>
          </Paper>
        </Grid>
      )}
    </>
  );
};

export default EnterpriseEditionSettings;
