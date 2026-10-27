import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadSimple } from '../../../../utils/api-types';

interface Props { payload?: PayloadSimple }

// Customized catalogue payload: its base, a newer base to rebase on, a conflict to resolve.
const PayloadVariantBadges: FunctionComponent<Props> = ({ payload }) => {
  const { t } = useFormatter();
  if (!payload?.payload_variant) {
    return null;
  }
  return (
    <>
      <Chip label={t('Customized')} />
      {payload.payload_base_version && <Chip label={t('Based on v{version}', { version: payload.payload_base_version })} />}
      {payload.payload_conflict && <Chip label={t('Conflict')} severity="high" />}
      {!payload.payload_conflict && payload.payload_newer_base_version && (
        <Chip label={t('New base v{version}', { version: payload.payload_newer_base_version })} severity="medium" />
      )}
    </>
  );
};

export default PayloadVariantBadges;
