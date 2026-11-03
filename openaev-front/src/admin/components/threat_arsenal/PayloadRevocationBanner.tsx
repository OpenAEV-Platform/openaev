import { Alert, AlertTitle } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchPayloadRevocation } from '../../../actions/payloads/payload-revocation-actions';
import { useFormatter } from '../../../components/i18n';
import { type PayloadRevocationOutput } from '../../../utils/api-types';

interface Props { payloadId: string }

const PayloadRevocationBanner: FunctionComponent<Props> = ({ payloadId }) => {
  const { t, nsd } = useFormatter();
  const [revocation, setRevocation] = useState<PayloadRevocationOutput | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchPayloadRevocation(payloadId)
      .then((response) => {
        if (!cancelled) setRevocation(response.data as PayloadRevocationOutput);
      })
      .catch(() => {
        if (!cancelled) setRevocation(null);
      });
    return () => {
      cancelled = true;
    };
  }, [payloadId]);

  if (!revocation) {
    return null;
  }
  // A revocation from the revocation list has no user: it comes from Filigran.
  const inTenant = revocation.content_revocation_source === 'tenant';
  const base = revocation.content_revocation_inherited_from;
  let title = t(inTenant ? 'Revoked in this tenant' : 'Revoked by Filigran');
  if (base) {
    title = inTenant
      ? t('Its Filigran base v{version} is revoked in this tenant', { version: base })
      : t('Its Filigran base v{version} is revoked by Filigran', { version: base });
  }
  const by = inTenant ? revocation.content_revocation_revoked_by : undefined;
  const at = revocation.content_revocation_revoked_at ? nsd(revocation.content_revocation_revoked_at) : undefined;
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
      {revocation.content_revocation_reason && <div>{`${t('Reason')}: ${revocation.content_revocation_reason}`}</div>}
    </Alert>
  );
};

export default PayloadRevocationBanner;
