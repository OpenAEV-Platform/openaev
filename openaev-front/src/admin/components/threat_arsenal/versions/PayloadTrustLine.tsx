import { Chip } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { getPayloadSourceLabel } from '../utils/threatArsenalStatusUtils';

interface Props {
  action: Pick<ThreatArsenalAction, 'action_payload' | 'action_payload_version' | 'action_payload_managed' | 'action_payload_customized'>;
  // Usages still on another version of the payload.
  toMove?: number;
  // On a customized copy, the newer Filigran version it can take.
  newBase?: string;
}

// What runs and from whom: the payload version and its source, and what waits to be updated.
const PayloadTrustLine: FunctionComponent<Props> = ({ action, toMove = 0, newBase }) => {
  const { t } = useFormatter();
  const theme = useTheme();
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
        gap: theme.spacing(1),
      }}
    >
      {action.action_payload_version && <Chip label={`v${action.action_payload_version}`} />}
      <Chip label={t(getPayloadSourceLabel(action))} severity={action.action_payload_managed ? 'info' : 'neutral'} />
      {toMove > 0 && <Chip label={t('{count} to move', { count: String(toMove) })} severity="medium" />}
      {newBase && <Chip label={t('New base v{version}', { version: newBase })} severity="info" />}
    </div>
  );
};

export default PayloadTrustLine;
