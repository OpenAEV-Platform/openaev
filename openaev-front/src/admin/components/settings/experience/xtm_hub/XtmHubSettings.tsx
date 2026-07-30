import { HubOutlined } from '@mui/icons-material';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';
import { useContext, useEffect, useRef } from 'react';

import type { LoggedHelper } from '../../../../../actions/helper';
import { fetchXtmHubRegistration, refreshConnectivity } from '../../../../../actions/xtmhub/xtmhub-actions';
import GradientButton from '../../../../../components/GradientButton';
import { useFormatter } from '../../../../../components/i18n';
import InfoChip from '../../../../../components/InfoChip';
import { useHelper } from '../../../../../store';
import { type PlatformSettings, type XtmHubRegistrationOutput } from '../../../../../utils/api-types';
import { XTM_HUB_DEFAULT_URL } from '../../../../../utils/Environment';
import { useAppDispatch } from '../../../../../utils/hooks';
import useAuth from '../../../../../utils/hooks/useAuth';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import ExperienceCard from '../ExperienceCard';
import XtmHubRegisteredSection from './XtmHubRegisteredSection';
import XtmHubTab from './XtmHubTab';
import XtmHubUnregisteredSection from './XtmHubUnregisteredSection';

const XtmHubSettings: React.FC = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { isXTMHubAccessible } = useAuth();
  const registration: XtmHubRegistrationOutput | null = useHelper((helper: LoggedHelper) => helper.getXtmHubRegistration());
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const hasFetchedRegistration = useRef(false);
  const hasRefreshedConnectivity = useRef(false);

  useEffect(() => {
    if (hasFetchedRegistration.current) return;
    hasFetchedRegistration.current = true;
    dispatch(fetchXtmHubRegistration());
  }, []);

  useEffect(() => {
    if (!registration?.tenant_xtmhub_registration_token || hasRefreshedConnectivity.current || ability.cannot(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS)) {
      return;
    }

    hasRefreshedConnectivity.current = true;
    dispatch(refreshConnectivity());
  }, [registration?.tenant_xtmhub_registration_token]);

  const registrationStatus = registration?.tenant_xtmhub_registration_status;
  const isXTMHubRegistered = registrationStatus === 'REGISTERED' || registrationStatus === 'LOST_CONNECTIVITY';
  const canInteract = isXTMHubAccessible && !!settings.xtm_hub_reachable;
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const hubUrl = settings?.xtm_hub_url ?? XTM_HUB_DEFAULT_URL;

  const statusChip = (() => {
    if (registrationStatus === 'REGISTERED') return <InfoChip label={t('Connected')} tone="green" />;
    if (registrationStatus === 'LOST_CONNECTIVITY') return <InfoChip label={t('Connectivity lost')} tone="red" />;
    return <InfoChip label={t('Not connected')} tone="accent" />;
  })();

  const buildFooter = (handleOpen?: () => void) => (isXTMHubRegistered
    ? (
        <>
          <Button
            variant="outlined"
            color="error"
            onClick={handleOpen}
            disabled={!handleOpen}
          >
            {t('Disconnect XTM Hub')}
          </Button>
          <GradientButton
            component="a"
            href={hubUrl}
            target="_blank"
            rel="noreferrer"
          >
            {t('Go to the Hub')}
          </GradientButton>
        </>
      )
    : (
        <>
          <Button
            variant="outlined"
            component="a"
            href={hubUrl}
            target="_blank"
            rel="noreferrer"
          >
            {t('Explore XTM Hub')}
          </Button>
          <GradientButton
            onClick={handleOpen}
            disabled={!handleOpen}
          >
            {t('Connect to XTM Hub')}
          </GradientButton>
        </>
      ));

  const renderCard = (handleOpen?: () => void) => (
    <ExperienceCard
      icon={<HubOutlined />}
      overline={t('Filigran Experience')}
      title={t('XTM Hub')}
      accent={theme.palette.xtmhub.main}
      statusChip={statusChip}
      footer={buildFooter(handleOpen)}
      testId="experience-xtm-hub-card"
    >
      {isXTMHubRegistered ? <XtmHubRegisteredSection /> : <XtmHubUnregisteredSection />}
    </ExperienceCard>
  );

  if (canInteract && canManage) {
    return <XtmHubTab renderTrigger={handleOpen => renderCard(handleOpen)} />;
  }
  return renderCard();
};

export default XtmHubSettings;
