import { Chip, Text } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction } from '../../../../utils/api-types';

interface Props {
  action: Pick<ThreatArsenalAction, 'action_payload' | 'action_payload_version' | 'action_payload_managed' | 'action_payload_revoked'>;
  updatedAt?: string;
}

// One line answering "what runs, from whom, can it run": version, source and revocation.
const PayloadTrustLine: FunctionComponent<Props> = ({ action, updatedAt }) => {
  const { t, nsd } = useFormatter();
  if (!action.action_payload) {
    return null;
  }
  return (
    <div
      data-testid="payload-trust-line"
      style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 8,
      }}
    >
      {action.action_payload_version && <Chip label={`v${action.action_payload_version}`} />}
      <Chip label={t(action.action_payload_managed ? 'Filigran' : 'Local')} severity={action.action_payload_managed ? 'info' : 'neutral'} />
      {action.action_payload_revoked && <Chip label={t('Revoked: does not run')} severity="critical" />}
      {updatedAt && <Text variant="content-caption" className="text-default-secondary">{`${t('Updated')} ${nsd(updatedAt)}`}</Text>}
    </div>
  );
};

export default PayloadTrustLine;
