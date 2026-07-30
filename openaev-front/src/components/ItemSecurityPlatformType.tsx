import { HelpOutlineOutlined } from '@mui/icons-material';
import { Chip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { securityPlatformTypeColor, securityPlatformTypeIcon, securityPlatformTypeLabel } from './securityPlatformType';

interface Props {
  type?: string;
  size?: 'small' | 'medium';
}

// A "tile" chip for a security platform type: left icon + canonical label,
// tinted with the type's distinct accent color (single source of truth in
// securityPlatformType.ts). Lookup is case-insensitive so a stored "Siem"
// still resolves to "SIEM"; unknown types fall back gracefully.
const ItemSecurityPlatformType: FunctionComponent<Props> = ({ type, size = 'small' }) => {
  const theme = useTheme();

  const Icon = securityPlatformTypeIcon(type) ?? HelpOutlineOutlined;
  const accent = securityPlatformTypeColor(type) ?? theme.palette.text.secondary;
  const label = securityPlatformTypeLabel(type);

  return (
    <Chip
      size={size}
      variant="outlined"
      icon={(
        <Icon sx={{
          fontSize: 15,
          color: `${accent} !important`,
        }}
        />
      )}
      label={label}
      sx={{
        'height': 24,
        'fontSize': 11,
        'fontWeight': 600,
        'letterSpacing': '0.03em',
        'borderRadius': 1,
        'color': accent,
        'borderColor': alpha(accent, 0.4),
        'backgroundColor': alpha(accent, 0.08),
        // Design-system exception: platform types are acronyms (EDR, SIEM...)
        // and must keep their true case - opt out of the global MuiChip
        // lowercase + first-letter-uppercase override.
        'textTransform': 'none',
        '&::first-letter': { textTransform: 'none' },
        '& .MuiChip-label': {
          'textTransform': 'none',
          '&::first-letter': { textTransform: 'none' },
        },
        '& .MuiChip-icon': { marginLeft: 0.5 },
      }}
    />
  );
};

export default ItemSecurityPlatformType;
