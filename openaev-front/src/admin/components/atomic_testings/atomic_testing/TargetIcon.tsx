import { Groups3Outlined, PersonOutlined, SmartToyOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';

import PlatformIcon from '../../../../components/PlatformIcon';
import { type InjectTarget } from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';

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
      case 'ASSETS':
        return <PlatformIcon platform={target?.target_subtype ?? 'Unknown'} width={glyphSize} />;
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
