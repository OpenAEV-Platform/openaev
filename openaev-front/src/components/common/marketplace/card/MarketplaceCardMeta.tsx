import { Chip } from '@filigran/design-system';
import { type ReactNode } from 'react';

import PlatformIcon from '../../../PlatformIcon';

const MAX_PLATFORMS = 3;

interface Props {
  status?: ReactNode;
  platforms: string[];
}

// Bottom left of a marketplace card: the item's status, then its first platforms and how many more.
const MarketplaceCardMeta = ({ status, platforms }: Props) => (
  <div style={{
    display: 'flex',
    alignItems: 'center',
    gap: 4,
  }}
  >
    {status}
    {platforms.slice(0, MAX_PLATFORMS).map(platform => <PlatformIcon key={platform} width={16} platform={platform} tooltip />)}
    {platforms.length > MAX_PLATFORMS && <Chip label={`+${platforms.length - MAX_PLATFORMS}`} />}
  </div>
);

export default MarketplaceCardMeta;
