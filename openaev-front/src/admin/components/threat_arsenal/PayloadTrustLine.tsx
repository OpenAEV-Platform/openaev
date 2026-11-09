import { Chip, Text } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type PayloadSimple } from '../../../utils/api-types';
import { payloadOrigin } from './payloadOrigin';
import PayloadOriginChip from './PayloadOriginChip';

interface Props {
  payload?: PayloadSimple;
  updatedAt?: string;
}

// One line answering "what runs, from whom, can it run": version, source, revocation and older versions
// still in use. The same line on every payload surface, so the object never changes shape.
const PayloadTrustLine: FunctionComponent<Props> = ({ payload, updatedAt }) => {
  const { t, nsd } = useFormatter();
  if (!payload) {
    return null;
  }
  const olderInUse = payload.payload_older_versions_in_use ?? 0;
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
      {payload.payload_version && <Chip label={`v${payload.payload_version}`} />}
      <PayloadOriginChip origin={payloadOrigin(payload)} />
      {payload.payload_revoked && <Chip label={t('Revoked: does not run')} severity="critical" />}
      {olderInUse > 0 && (
        <Chip label={t('{count} to migrate', { count: String(olderInUse) })} severity="medium" />
      )}
      {payload.payload_newer_base_version && (
        <Chip label={t('New base v{version}', { version: payload.payload_newer_base_version })} severity="info" />
      )}
      {updatedAt && <Text variant="content-caption" className="text-default-secondary">{`${t('Updated')} ${nsd(updatedAt)}`}</Text>}
    </div>
  );
};

export default PayloadTrustLine;
