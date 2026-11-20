import { Alert } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';

interface Props { version: PayloadVersionOutput }

const PayloadRevocationBanner: FunctionComponent<Props> = ({ version }) => {
  const { t, nsd } = useFormatter();
  // A revocation from the revocation list has no user: it comes from Filigran.
  const inTenant = !version.payload_revoked_by_catalogue;
  const by = inTenant ? version.payload_revoked_by : undefined;
  const at = version.payload_revoked_at ? nsd(version.payload_revoked_at) : undefined;
  let when = '';
  if (by && at) {
    when = t('by {name} on {date}', {
      name: by,
      date: at,
    });
  } else if (by) {
    when = t('by {name}', { name: by });
  } else if (at) {
    when = t('on {date}', { date: at });
  }
  const title = [t(inTenant ? 'Revoked in this tenant' : 'Revoked by Filigran'), when].filter(Boolean).join(' ');

  return (
    <Alert
      severity="error"
      title={title}
      description={version.payload_revocation_reason && `${t('Reason')}: ${version.payload_revocation_reason}`}
    />
  );
};

export default PayloadRevocationBanner;
