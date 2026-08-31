import {
  AccountTreeOutlined,
  AutoAwesomeOutlined,
  DomainOutlined,
  RocketLaunchOutlined,
  SecurityOutlined,
  SupportAgentOutlined,
  VpnKeyOutlined,
} from '@mui/icons-material';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Switch, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';
import { type ChangeEvent, useState } from 'react';

import { updateChatbotAiCguStatus, updatePlatformEnterpriseEditionParameters } from '../../../../actions/Application';
import type { LoggedHelper } from '../../../../actions/helper';
import { useFormatter } from '../../../../components/i18n';
import InfoChip from '../../../../components/InfoChip';
import { useHelper } from '../../../../store';
import type { PlatformSettings, SettingsEnterpriseEditionUpdateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { Can, useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import FiligranAiCguDialog from '../../ariane/FiligranAiCguDialog';
import EnterpriseEditionButton from '../../common/entreprise_edition/EnterpriseEditionButton';
import ExperienceCard, { ExperienceHeadline } from './ExperienceCard';
import ExperienceDetailRow from './ExperienceDetailRow';
import ExperienceFeatureTile from './ExperienceFeatureTile';

const EnterpriseEditionSettings: React.FC = () => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { t, fldt } = useFormatter();
  const ability = useAbility();
  const [openEEChanges, setOpenEEChanges] = useState(false);
  const [openValidateTermsOfUse, setOpenValidateTermsOfUse] = useState(false);
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const { openDialog } = useEnterpriseEdition();

  const isEnterpriseEditionActivated = settings.platform_license?.license_is_enterprise;
  const isEnterpriseEditionByConfig = settings.platform_license?.license_is_by_configuration;
  const isLicenseExpired = settings.platform_license?.license_is_expired;
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

  const statusChip = (() => {
    if (!isEnterpriseEditionActivated) return <InfoChip label={t('Community edition')} tone="accent" />;
    if (isLicenseExpired) return <InfoChip label={t('Expired')} tone="red" />;
    return <InfoChip label={t('Activated')} tone="green" />;
  })();

  const activatedFooter = !isEnterpriseEditionByConfig
    ? (
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
          <Button
            size="small"
            variant="outlined"
            color="primary"
            onClick={() => setOpenEEChanges(true)}
          >
            {t('Disable Enterprise Edition')}
          </Button>
          <EnterpriseEditionButton />
        </Can>
      )
    : undefined;

  const canManageTenantSettings = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);

  const unregisteredFooter = canManageTenantSettings
    ? (
        <Button
          variant="outlined"
          color="ee"
          startIcon={<RocketLaunchOutlined />}
          onClick={() => openDialog()}
        >
          {t('Try OpenAEV Enterprise Edition')}
        </Button>
      )
    : (
        <Button
          variant="outlined"
          color="ee"
          component="a"
          href="https://filigran.io/services/openaev-enterprise-edition/"
          target="_blank"
          rel="noopener noreferrer"
        >
          {t('Try OpenAEV Enterprise Edition')}
        </Button>
      );

  return (
    <ExperienceCard
      icon={<RocketLaunchOutlined />}
      overline={t('Filigran Experience')}
      title={t('Enterprise Edition')}
      accent={theme.palette.ee.main}
      statusChip={statusChip}
      footer={isEnterpriseEditionActivated ? activatedFooter : unregisteredFooter}
      testId="experience-enterprise-edition-card"
    >
      {isEnterpriseEditionActivated
        ? (
            <div>
              <ExperienceDetailRow label={t('Organisation')}>
                <InfoChip
                  label={settings.platform_license?.license_customer ?? t('Not applicable')}
                  tone="accent"
                />
              </ExperienceDetailRow>
              <ExperienceDetailRow label={t('Scope')}>
                <InfoChip
                  label={settings.platform_license?.license_is_global ? t('Global') : t('Current instance')}
                  tone="accent"
                />
              </ExperienceDetailRow>
              {!settings.platform_license?.license_is_expired && settings.platform_license?.license_is_prevention && (
                <Alert severity="warning" variant="outlined" sx={{ marginY: 1 }}>
                  {t('Your Enterprise Edition license will expire in less than 3 months.')}
                </Alert>
              )}
              {!settings.platform_license?.license_is_validated && settings.platform_license?.license_is_valid_cert && (
                <Alert severity="error" variant="outlined" sx={{ marginY: 1 }}>
                  {t('Your Enterprise Edition license is expired. Please contact your Filigran representative.')}
                </Alert>
              )}
              <ExperienceDetailRow label={t('Start date')}>
                <InfoChip
                  label={fldt(settings.platform_license?.license_start_date)}
                  tone={settings.platform_license?.license_is_expired ? 'red' : 'green'}
                />
              </ExperienceDetailRow>
              <ExperienceDetailRow label={t('Expiration date')}>
                <InfoChip
                  label={fldt(settings.platform_license?.license_expiration_date)}
                  tone={settings.platform_license?.license_is_expired ? 'red' : 'green'}
                />
              </ExperienceDetailRow>
              <ExperienceDetailRow label={t('License type')} divider={canManageSettings}>
                <InfoChip
                  label={settings.platform_license?.license_type ?? t('Not applicable')}
                  tone="accent"
                />
              </ExperienceDetailRow>
              {canManageSettings && (
                <ExperienceDetailRow label={t('XTM One (Agentic IA)')} divider={false}>
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
                </ExperienceDetailRow>
              )}
              {openValidateTermsOfUse && (
                <FiligranAiCguDialog
                  open={openValidateTermsOfUse}
                  onClose={() => setOpenValidateTermsOfUse(false)}
                />
              )}
            </div>
          )
        : (
            <>
              <ExperienceHeadline>
                {t('Unlock powerful capabilities with OpenAEV Enterprise Edition')}
              </ExperienceHeadline>
              <Typography variant="body2" color="text.secondary">
                {t('Get enterprise-grade automation, remediation, and deployment flexibility - trusted by governments, financial institutions, and global enterprises. Deployment flexibility with SaaS, on-premise, and Bring-Your-Own-Cloud to match your needs.')}
              </Typography>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                  gap: theme.spacing(1.5),
                }}
              >
                <ExperienceFeatureTile accent={theme.palette.ee.main} icon={<AutoAwesomeOutlined />} label={t('AI-powered scenario generation & remediation')} />
                <ExperienceFeatureTile accent={theme.palette.ee.main} icon={<SecurityOutlined />} label={t('Agentless through your EDR')} />
                <ExperienceFeatureTile accent={theme.palette.ee.main} icon={<VpnKeyOutlined />} label={t('SSO')} />
                <ExperienceFeatureTile accent={theme.palette.ee.main} icon={<DomainOutlined />} label={t('Multi-Tenancy')} />
                <ExperienceFeatureTile accent={theme.palette.ee.main} icon={<AccountTreeOutlined />} label={t('Autonomous scenarios with chaining')} />
                <ExperienceFeatureTile accent={theme.palette.ee.main} icon={<SupportAgentOutlined />} label={t('Dedicated technical support')} />
              </div>
            </>
          )}

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
    </ExperienceCard>
  );
};

export default EnterpriseEditionSettings;
