import { Text } from '@filigran/design-system';
import type React from 'react';

import type { LoggedHelper } from '../../../../../actions/helper';
import { useFormatter } from '../../../../../components/i18n';
import InfoChip from '../../../../../components/InfoChip';
import { useHelper } from '../../../../../store';
import { ExperienceHeadline } from '../ExperienceCard';
import ExperienceDetailRow from '../ExperienceDetailRow';

const XtmHubRegisteredSection: React.FC = () => {
  const { t, fldt } = useFormatter();
  const registration = useHelper((helper: LoggedHelper) => helper.getXtmHubRegistration());

  const isConnected = registration?.tenant_xtmhub_registration_status === 'REGISTERED';
  const connectionDate = registration?.tenant_xtmhub_registration_date
    ? fldt(registration.tenant_xtmhub_registration_date)
    : '-';
  const connectedBy = registration?.tenant_xtmhub_registration_user_name ?? '-';
  // Empty until the platform registers, or refreshes its connectivity, with a hub that returns it.
  const workspace = registration?.tenant_xtmhub_registration_organization_name;

  return (
    <>
      <ExperienceHeadline>
        {t('Experiment valuable threat management resources in the XTM Hub')}
      </ExperienceHeadline>
      <div>
        <ExperienceDetailRow label={t('Connection status')}>
          <InfoChip
            label={isConnected ? t('Connected') : t('Connectivity lost')}
            tone={isConnected ? 'green' : 'red'}
          />
        </ExperienceDetailRow>
        <ExperienceDetailRow label={t('Connection date')}>
          <InfoChip label={connectionDate} tone="accent" />
        </ExperienceDetailRow>
        <ExperienceDetailRow label={t('Connected by')} divider={!!workspace}>
          <Text variant="content-base">{connectedBy}</Text>
        </ExperienceDetailRow>
        {workspace && (
          <ExperienceDetailRow label={t('Workspace')} divider={false}>
            <Text variant="content-base" data-testid="xtm-hub-workspace">{workspace}</Text>
          </ExperienceDetailRow>
        )}
      </div>
    </>
  );
};

export default XtmHubRegisteredSection;
