import { useQueryParameter } from '../../../utils/Environment';
import ChannelPlayer from './ChannelPlayer';
import ChannelPreview from './ChannelPreview';

const Channel = () => {
  const [preview] = useQueryParameter(['preview']);
  if (preview === 'true') {
    return <ChannelPreview />;
  }
  return <ChannelPlayer />;
};

export default Channel;
