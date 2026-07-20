import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import PlatformIcon from './PlatformIcon';

interface PlatformIconGroupProps {
  platforms?: string[] | null;
  width?: number;
  tooltip?: boolean;
}

// Renders a row of platform icons, or a neutral "-" when there is no platform to show. Centralizes
// the "no platform / no inject" case so lists never fall back to a pointless, oddly-sized question
// mark. Unknown platform values inside the list render nothing (see PlatformIcon).
const PlatformIconGroup: FunctionComponent<PlatformIconGroupProps> = ({ platforms, width = 20, tooltip = true }) => {
  const theme = useTheme();
  const values = (platforms ?? []).filter(Boolean);
  if (values.length === 0) {
    return <span>-</span>;
  }
  return (
    <>
      {values.map(platform => (
        <PlatformIcon
          key={platform}
          platform={platform}
          tooltip={tooltip}
          width={width}
          marginRight={theme.spacing(2)}
        />
      ))}
    </>
  );
};

export default PlatformIconGroup;
