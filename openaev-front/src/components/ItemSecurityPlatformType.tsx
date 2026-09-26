import { Chip } from '@filigran/design-system';
import { HelpOutlineOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
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
const ItemSecurityPlatformType: FunctionComponent<Props> = ({ type }) => {
  const theme = useTheme();

  const Icon = securityPlatformTypeIcon(type) ?? HelpOutlineOutlined;
  const accent = securityPlatformTypeColor(type) ?? theme.palette.text.secondary;
  const label = securityPlatformTypeLabel(type);

  return (
    <Chip
      startIcon={(
        <Icon sx={{
          fontSize: 15,
          color: `${accent} !important`,
        }}
        />
      )}
      label={label}
      color={accent}
    />
  );
};

export default ItemSecurityPlatformType;
