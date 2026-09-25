import { Chip } from '@filigran/design-system';

type Props = { arch?: string };

// Architecture is only meaningful for OS-bound assets; absent / unknown values render a neutral dash
// rather than a misleading "Unknown". Present values render as a tile (like status / criticality).
const EndpointArchFragment = ({ arch }: Props) => {
  if (!arch || arch === 'Unknown') {
    return <span>-</span>;
  }
  return <Chip label={arch} />;
};

export default EndpointArchFragment;
