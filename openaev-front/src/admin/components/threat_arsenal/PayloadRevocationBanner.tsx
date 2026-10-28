import { Alert, AlertTitle } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchPayloadRevocation } from '../../../actions/payloads/payload-revocation-actions';
import { useFormatter } from '../../../components/i18n';
import { type PayloadRevocationOutput } from '../../../utils/api-types';

interface Props { payloadId: string }

const PayloadRevocationBanner: FunctionComponent<Props> = ({ payloadId }) => {
  const { t, nsdt } = useFormatter();
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
  const revokedBy = revocation.content_revocation_source === 'tenant'
    ? revocation.content_revocation_revoked_by
    : 'Filigran';
  const meta = [revocation.content_revocation_revoked_at && nsdt(revocation.content_revocation_revoked_at), revokedBy]
    .filter(Boolean)
    .join(' · ');

  return (
    <Alert severity="error">
      <AlertTitle>{meta ? `${t('Revoked')} · ${meta}` : t('Revoked')}</AlertTitle>
      {revocation.content_revocation_reason && `${t('Reason')}: ${revocation.content_revocation_reason}`}
    </Alert>
  );
};

export default PayloadRevocationBanner;
