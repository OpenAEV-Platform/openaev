import { type FunctionComponent } from 'react';

import { type Article, type Channel } from '../../../../../../utils/api-types';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';
import ChannelIcon from '../../../../components/channels/ChannelIcon';
import ExpectationLine from './ExpectationLine';

interface Props {
  channel: Channel;
  article: Article;
  expectation: InjectExpectationsStore;
}

const ChannelExpectation: FunctionComponent<Props> = ({
  channel,
  article,
  expectation,
}) => {
  return (
    <ExpectationLine
      expectation={expectation}
      info={channel.channel_name}
      title={article.article_name ?? ''}
      icon={<ChannelIcon type={channel.channel_type} size="small" />}
    />

  );
};

export default ChannelExpectation;
