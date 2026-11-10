import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { PAYLOAD_ORIGIN_LABELS, type PayloadOrigin } from './payloadOrigin';

interface Props { origin?: PayloadOrigin | null }

const PayloadOriginChip: FunctionComponent<Props> = ({ origin }) => {
  const { t } = useFormatter();
  if (!origin) {
    return null;
  }
  return <Chip label={t(PAYLOAD_ORIGIN_LABELS[origin])} severity={origin === 'CATALOG' ? 'info' : 'neutral'} />;
};

export default PayloadOriginChip;
