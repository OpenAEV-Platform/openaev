import { Groups3Outlined, PersonOutlined, SmartToyOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';

import PlatformIcon, { hasPlatformIcon } from '../../../../components/PlatformIcon';
import { type InjectTarget } from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import { type AssetCategory } from '../../assets/asset-categories';
import AssetCategoryIcon from '../../assets/AssetCategoryIcon';

// Asset categories whose OS platform brand icon (Windows / Linux / macOS / iOS / Android) is the
// meaningful glyph. Every other category has no OS platform (its target_subtype is "Unknown"), so it
// is represented by its taxonomy glyph via AssetCategoryIcon instead of an empty box.
const OS_PLATFORM_CATEGORIES = new Set<AssetCategory>(['HOST', 'MOBILE_DEVICE']);

interface Props {
  target: InjectTarget;
  size?: number;
}

// Shared, framed target glyph used by both the target list row and the results
// header so the selected target reads identically in both places.
const TargetIcon: FunctionComponent<Props> = ({ target, size = 32 }) => {
  const theme = useTheme();
  const glyphSize = Math.round(size * 0.56);

  const glyph = (() => {
    switch (target.target_type) {
      case 'ASSETS_GROUPS':
        return <SelectGroup sx={{ fontSize: glyphSize }} />;
      case 'ASSETS': {
        // Host-like assets keep their OS/platform brand icon. Other categories (web application,
        // cloud, network device, identity, ...) have no meaningful OS platform, so the platform is
        // "Unknown" and PlatformIcon would render nothing - fall back to the asset category glyph.
        const category = target?.target_category as AssetCategory | undefined;
        const platform = target?.target_subtype;
        // Only take the platform path when it will actually paint something: discovered hosts
        // often carry an "Unknown" platform, for which PlatformIcon renders nothing - the
        // category glyph is always a better fallback than an empty frame.
        if (hasPlatformIcon(platform) && (!category || OS_PLATFORM_CATEGORIES.has(category))) {
          return <PlatformIcon platform={platform as string} width={glyphSize} />;
        }
        return <AssetCategoryIcon category={category ?? null} sx={{ fontSize: glyphSize }} />;
      }
      case 'TEAMS':
        return <Groups3Outlined sx={{ fontSize: glyphSize }} />;
      case 'PLAYERS':
        return <PersonOutlined sx={{ fontSize: glyphSize }} />;
      case 'AI_TARGETS':
        return <SmartToyOutlined sx={{ fontSize: glyphSize }} />;
      case 'AGENT':
        return (
          <img
            src={buildTenantApiPath(`/api/images/executors/icons/${target.target_subtype}`)}
            alt={target.target_subtype}
            style={{
              width: glyphSize,
              height: glyphSize,
              borderRadius: 4,
            }}
          />
        );
      default:
        return null;
    }
  })();

  return (
    <Box
      aria-hidden
      sx={{
        width: size,
        height: size,
        flexShrink: 0,
        borderRadius: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: alpha(theme.palette.text.primary, 0.04),
      }}
    >
      {glyph}
    </Box>
  );
};

export default TargetIcon;
