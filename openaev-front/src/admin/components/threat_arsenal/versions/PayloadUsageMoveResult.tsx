import { Alert, Button } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadUsageRefusal } from '../../../../utils/api-types';
import { type UsageMove } from './useUsageMove';

const REFUSAL_LABELS: Record<PayloadUsageRefusal['reason'], string> = {
  NOT_ON_THIS_VERSION: 'No longer on this version',
  MISSING_RIGHT: 'You cannot update it',
  NOT_COMPATIBLE: 'This version needs fields it leaves empty',
};

interface Props { move: UsageMove }

const PayloadUsageMoveResult: FunctionComponent<Props> = ({ move }) => {
  const { t } = useFormatter();
  const { result } = move;
  if (!result) return null;

  return (
    <Alert
      severity={result.refused.length > 0 ? 'warning' : 'success'}
      title={`${t('Moved')}: ${result.moved_ids.length}`}
      description={result.refused.map(refusal => (
        <div key={refusal.usage_id}>{`${t('Not moved')}: ${move.nameOf(refusal.usage_id)} (${t(REFUSAL_LABELS[refusal.reason])})`}</div>
      ))}
      action={<Button priority="secondary" size="sm" onClick={move.clearResult}>{t('Close')}</Button>}
    />
  );
};

export default PayloadUsageMoveResult;
