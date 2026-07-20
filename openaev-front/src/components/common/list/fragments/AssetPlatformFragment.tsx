import { useTheme } from '@mui/material/styles';

import PlatformIcon from '../../../PlatformIcon';

type Props = {
  platform?: string;
  compact?: boolean;
};

const AssetPlatformFragment = ({ platform, compact }: Props) => {
  const theme = useTheme();
  // A platform is only meaningful for OS-bound assets (endpoints). Non-endpoint assets (identities,
  // AI targets, cloud/SaaS resources, ...) have no platform, so render a neutral dash instead of a
  // misleading "Unknown" + placeholder icon.
  if (!platform || platform === 'Unknown') {
    return <>-</>;
  }
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
    }}
    >
      <PlatformIcon
        platform={platform}
        width={20}
        marginRight={theme.spacing(2)}
      />
      {!compact && platform}
    </div>
  );
};

export default AssetPlatformFragment;
