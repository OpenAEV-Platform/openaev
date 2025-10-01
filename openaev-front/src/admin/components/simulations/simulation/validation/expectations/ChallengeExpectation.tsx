import { EmojiEventsOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { type Challenge } from '../../../../../../utils/api-types';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';
import ExpectationLine from './ExpectationLine';

interface Props {
  expectation: InjectExpectationsStore;
  challenge: Challenge;
}

const ChallengeExpectation: FunctionComponent<Props> = ({
  expectation,
  challenge,
}) => {
  return (
    <ExpectationLine
      expectation={expectation}
      info={challenge.challenge_category}
      title={challenge.challenge_name}
      icon={<EmojiEventsOutlined fontSize="small" />}
    />
  );
};

export default ChallengeExpectation;
