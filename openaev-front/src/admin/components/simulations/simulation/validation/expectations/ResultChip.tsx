import { Chip, type ChipSeverity } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';

interface Props { expectation: InjectExpectationsStore }

const ResultChip: FunctionComponent<Props> = ({ expectation }) => {
  // Standard hooks
  const { t } = useFormatter();

  const score = expectation.inject_expectation_score;
  const isPending = score == null;
  const isFail = !isPending && score < expectation.inject_expectation_expected_score;

  const label = () => {
    if (isPending) {
      if (expectation.inject_expectation_type === 'ARTICLE') {
        return t('Pending reading');
      }
      if (expectation.inject_expectation_type === 'CHALLENGE') {
        return t('Pending submission');
      }
      return t('Pending');
    }
    if (isFail) {
      return `${t('Failed')} (${score})`;
    }
    return `${t('Validated')} (${score})`;
  };

  const severity = (): ChipSeverity => {
    if (isPending) {
      return 'medium';
    }
    return isFail ? 'critical' : 'low';
  };

  return (
    <>
      <Chip label={String(expectation.inject_expectation_expected_score)} />
      <Chip severity={severity()} label={label() ?? ''} />
    </>
  );
};

export default ResultChip;
