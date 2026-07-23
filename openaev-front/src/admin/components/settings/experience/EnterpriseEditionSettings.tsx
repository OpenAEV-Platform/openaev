import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, List, ListItem, ListItemText, Paper, Switch, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';
import { type ChangeEvent, useContext, useState } from 'react';

import { updateChatbotAiCguStatus, updatePlatformEnterpriseEditionParameters } from '../../../../actions/Application';
import type { LoggedHelper } from '../../../../actions/helper';
import { useFormatter } from '../../../../components/i18n';
import InfoChip from '../../../../components/InfoChip';
import { useHelper } from '../../../../store';
import type { PlatformSettings, SettingsEnterpriseEditionUpdateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext, Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import FiligranAiCguDialog from '../../ariane/FiligranAiCguDialog';
import EnterpriseEditionButton from '../../common/entreprise_edition/EnterpriseEditionButton';

const EnterpriseEditionSettings: React.FC = () => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { t, fldt } = useFormatter();
  const ability = useContext(AbilityContext);
  const [openEEChanges, setOpenEEChanges] = useState(false);
  const [openValidateTermsOfUse, setOpenValidateTermsOfUse] = useState(false);
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  const isEnterpriseEditionActivated = settings.platform_license?.license_is_enterprise;
  const isEnterpriseEditionByConfig = settings.platform_license?.license_is_by_configuration;
  const isEnterpriseEdition = settings.platform_license?.license_is_validated === true;
  const canManageSettings = ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS);
  const updateEnterpriseEdition = (data: SettingsEnterpriseEditionUpdateInput) => dispatch(updatePlatformEnterpriseEditionParameters(data));

  const chatbotCguStatus = settings.filigran_chatbot_ai_cgu_status;
  const isCguPending = chatbotCguStatus === 'pending' || chatbotCguStatus === undefined;

  const handleCGUStatusChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (event.target.checked) {
      setOpenValidateTermsOfUse(true);
    } else {
      dispatch(updateChatbotAiCguStatus({ status: 'disabled' }));
    }
  };

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
              sx={{
                display: 'flex',
                alignItems: 'flex-end',
                marginBottom: 0,
                minHeight: theme.spacing(4.5),
              }}
            >
              {t('Enterprise Edition')}
            </Typography>
            {!isEnterpriseEditionByConfig && !isEnterpriseEdition && (
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
                <EnterpriseEditionButton />
              </Can>
            )}
            {!isEnterpriseEditionByConfig && isEnterpriseEdition && (
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
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
                        sx={{ marginTop: theme.spacing(6) }}
                        fontWeight="bold"
                      >
                        {t('However, your existing data will remain intact and will not be lost.')}
                      </Typography>
                    </Alert>
                  </DialogContent>
                  <DialogActions>
                    <Button
                      variant="outlined"
                      color="primary"
                      onClick={() => {
                        setOpenEEChanges(false);
                      }}
                    >
                      {t('Cancel')}
                    </Button>
                    <Button
                      variant="contained"
                      color="primary"
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
            sx={{
              padding: theme.spacing(0, 2, 2),
              borderRadius: 4,
              flexGrow: 1,
            }}
            variant="outlined"
          >
            <List sx={{ padding: 0 }}>
              <ListItem divider disableGutters>
                <ListItemText primary={t('Organisation')} />
                <InfoChip
                  label={settings.platform_license?.license_customer ?? t('Not applicable')}
                  tone="accent"
                />
              </ListItem>

              <ListItem divider disableGutters>
                <ListItemText primary={t('Scope')} />
                <InfoChip
                  label={settings.platform_license?.license_is_global ? t('Global') : t('Current instance')}
                  tone="accent"
                />
              </ListItem>
              {!settings.platform_license?.license_is_expired && settings.platform_license?.license_is_prevention && (
                <ListItem disableGutters>
                  <Alert severity="warning" variant="outlined" style={{ width: '100%' }}>
                    {t('Your Enterprise Edition license will expire in less than 3 months.')}
                  </Alert>
                </ListItem>
              )}
              {!settings.platform_license?.license_is_validated && settings.platform_license?.license_is_valid_cert && (
                <ListItem disableGutters>
                  <Alert severity="error" variant="outlined" style={{ width: '100%' }}>
                    {t('Your Enterprise Edition license is expired. Please contact your Filigran representative.')}
                  </Alert>
                </ListItem>
              )}
              <ListItem divider disableGutters>
                <ListItemText primary={t('Start date')} />
                <InfoChip
                  label={fldt(settings.platform_license?.license_start_date)}
                  tone={settings.platform_license?.license_is_expired ? 'red' : 'green'}
                />
              </ListItem>
              <ListItem divider disableGutters>
                <ListItemText primary={t('Expiration date')} />
                <InfoChip
                  label={fldt(settings.platform_license?.license_expiration_date)}
                  tone={settings.platform_license?.license_is_expired ? 'red' : 'green'}
                />
              </ListItem>
              <ListItem divider={!settings.platform_license?.license_is_prevention} disableGutters>
                <ListItemText primary={t('License type')} />
                <InfoChip
                  label={settings.platform_license?.license_type ?? t('Not applicable')}
                  tone="accent"
                />
              </ListItem>
              {canManageSettings && (
                <ListItem divider disableGutters>
                  <ListItemText primary={t('XTM One (Agentic IA)')} />
                  {isCguPending
                    ? (
                        <Button
                          size="small"
                          variant="outlined"
                          color="secondary"
                          onClick={() => setOpenValidateTermsOfUse(true)}
                          style={{ lineHeight: '12px' }}
                        >
                          {t('Validate the Filigran AI Terms')}
                        </Button>
                      )
                    : (
                        <Box sx={{ marginBlock: -0.75 }}>
                          <Switch
                            checked={chatbotCguStatus === 'enabled'}
                            onChange={handleCGUStatusChange}
                          />
                        </Box>
                      )}
                </ListItem>
              )}
            </List>
            {openValidateTermsOfUse && (
              <FiligranAiCguDialog
                open={openValidateTermsOfUse}
                onClose={() => setOpenValidateTermsOfUse(false)}
              />
            )}
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
              sx={{
                display: 'flex',
                alignItems: 'flex-end',
                marginBottom: 0,
                minHeight: theme.spacing(4.5),
              }}
            >
              {t('Enterprise Edition')}
            </Typography>
            {!isEnterpriseEditionActivated && (
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
                <EnterpriseEditionButton />
              </Can>
            )}
          </div>

          <Paper
            sx={{
              padding: theme.spacing(3),
              flexGrow: 1,
              display: 'flex',
              flexDirection: 'column',
              backgroundColor: theme.palette.background.paper,
              borderColor: theme.palette.border.primary,
            }}
            className="paper-for-grid"
            variant="outlined"
          >
            <Typography variant="h6" sx={{ marginBottom: theme.spacing(2) }}>
              {t('Unlock powerful capabilities with OpenAEV Enterprise Edition')}
            </Typography>
            <Typography sx={{ marginBottom: theme.spacing(5) }}>
              {t('Get enterprise-grade automation, remediation, and deployment flexibility - trusted by governments, financial institutions, and global enterprises. Deployment flexibility with SaaS, on-premise, and Bring-Your-Own-Cloud to match your needs.')}
            </Typography>
            <div
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: theme.spacing(2),
                marginBottom: theme.spacing(3),
              }}
            >
              <InfoChip label={t('AI-powered scenario generation & remediation')} tone="accent" />
              <InfoChip label={t('Agentless through your EDR')} tone="accent" />
              <InfoChip label={t('SSO')} tone="accent" />
              <InfoChip label={t('Multi-Tenancy')} tone="accent" />
              <InfoChip label={t('Autonomous scenarios with chaining')} tone="accent" />
              <InfoChip label={t('Dedicated technical support')} tone="accent" />
            </div>
            <Button
              size="small"
              variant="outlined"
              color="ee"
              component="a"
              href="https://filigran.io/services/openaev-enterprise-edition/"
              target="_blank"
              rel="noopener noreferrer"
              sx={{
                alignSelf: 'flex-start',
                marginTop: 'auto',
                textTransform: 'none',
                fontWeight: theme.typography.fontWeightBold,
              }}
            >
              {t('Try OpenAEV Enterprise Edition')}
            </Button>
          </Paper>
        </Grid>
      )}
    </>
  );
};

export default EnterpriseEditionSettings;
