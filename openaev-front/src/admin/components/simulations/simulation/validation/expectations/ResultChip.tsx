import { Chip } from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import colorStyles from '../../../../../../components/Color';
import { useFormatter } from '../../../../../../components/i18n';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';

const useStyles = makeStyles()(() => ({
  chipInList: {
    height: 20,
    borderRadius: 4,
    textTransform: 'uppercase',
    width: 200,
  },
  points: {
    height: 20,
    backgroundColor: 'rgba(236, 64, 122, 0.08)',
    border: '1px solid #ec407a',
    color: '#ec407a',
  },
}));

interface Props { expectation: InjectExpectationsStore }

const ResultChip: FunctionComponent<Props> = ({ expectation }) => {
  // Standard hooks
  const { classes } = useStyles();
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

  const color = () => {
    if (isFail()) {
      return colorStyles.orange;
    }
    return result
      ? colorStyles.green
      : colorStyles.blueGrey;
  };

  return (
    <>
      <Chip
        classes={{ root: classes.points }}
        label={expectation.inject_expectation_expected_score}
      />
      <Chip
        classes={{ root: classes.chipInList }}
        style={color()}
        label={label()}
      />
    </>
  );
};

export default ResultChip;
