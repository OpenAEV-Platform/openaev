import { Chip } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { getPayloadSourceLabel } from '../utils/threatArsenalStatusUtils';

interface Props { action: Pick<ThreatArsenalAction, 'action_payload' | 'action_payload_version' | 'action_payload_managed' | 'action_payload_customized'> }

// What runs and from whom: the payload version and its source.
const PayloadTrustLine: FunctionComponent<Props> = ({ action }) => {
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
    </div>
  );
};

export default PayloadTrustLine;
