import { Chip, type ChipSeverity } from '@filigran/design-system';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';

interface Props { expectation: InjectExpectationsStore }

const ResultChip: FunctionComponent<Props> = ({ expectation }) => {
  // Standard hooks
  const { t } = useFormatter();

  const result = !R.isEmpty(expectation.inject_expectation_results);

  const isFail = () => {
    return result
      && (expectation.inject_expectation_type === 'PREVENTION'
        && expectation.inject_expectation_expected_score !== expectation.inject_expectation_score);
  };

  const label = () => {
    if (result) {
      if (isFail()) {
        return `${t('Failed')} (${expectation.inject_expectation_score})`;
      }
      return `${t('Validated')} (${expectation.inject_expectation_score})`;
    }

    if (expectation.inject_expectation_type === 'ARTICLE') {
      return t('Pending reading');
    }
    if (expectation.inject_expectation_type === 'CHALLENGE') {
      return t('Pending submission');
    }
    if (expectation.inject_expectation_type === 'PREVENTION' || expectation.inject_expectation_type === 'DETECTION') {
      return t('Pending');
    }

    return null;
  };

  const severity = (): ChipSeverity => {
    if (isFail()) {
      return 'medium';
    }
    return result ? 'low' : 'neutral';
  };

  return (
    <>
      <Chip label={String(expectation.inject_expectation_expected_score)} />
      <Chip severity={severity()} label={label() ?? ''} />
    </>
  );
};

export default ResultChip;
