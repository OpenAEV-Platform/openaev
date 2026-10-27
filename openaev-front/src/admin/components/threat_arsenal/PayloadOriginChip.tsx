import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';

export type PayloadOrigin = 'CATALOG' | 'CUSTOMIZED' | 'LOCAL';

const LABELS: Record<PayloadOrigin, string> = {
  CATALOG: 'Filigran',
  CUSTOMIZED: 'Customized',
  LOCAL: 'Local',
};

interface Props { origin?: PayloadOrigin | null }

const PayloadOriginChip: FunctionComponent<Props> = ({ origin }) => {
  const { t } = useFormatter();
  if (!origin) {
    return null;
  }
  return <Chip label={t(LABELS[origin])} severity={origin === 'CATALOG' ? 'info' : 'neutral'} />;
};

export default PayloadOriginChip;
