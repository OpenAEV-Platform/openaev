import { Alert, AlertTitle } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';

interface Props { version: PayloadVersionOutput }

const PayloadRevocationBanner: FunctionComponent<Props> = ({ version }) => {
  const { t, nsd } = useFormatter();
  // A revocation from the revocation list has no user: it comes from Filigran.
  const inTenant = !version.payload_revoked_by_catalogue;
  const title = t(inTenant ? 'Revoked in this tenant' : 'Revoked by Filigran');
  const by = inTenant ? version.payload_revoked_by : undefined;
  const at = version.payload_revoked_at ? nsd(version.payload_revoked_at) : undefined;
  let when = '';
  if (by && at) {
    when = String(t('by {name} on {date}', {
      name: by,
      date: at,
    }));
  } else if (by) {
    when = String(t('by {name}', { name: by }));
  } else if (at) {
    when = String(t('on {date}', { date: at }));
  }

  return (
    <Alert severity="error">
      <AlertTitle>
        <strong>{title}</strong>
        {when && ` ${when}`}
      </AlertTitle>
      {version.payload_revocation_reason && <div>{`${t('Reason')}: ${version.payload_revocation_reason}`}</div>}
    </Alert>
  );
};

export default PayloadRevocationBanner;
