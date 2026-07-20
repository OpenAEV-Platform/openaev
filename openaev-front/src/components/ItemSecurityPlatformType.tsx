import {
  BoltOutlined,
  DevicesOtherOutlined,
  FingerprintOutlined,
  HelpOutlineOutlined,
  HubOutlined,
  LanOutlined,
  SecurityOutlined,
  SmartToyOutlined,
  StorageOutlined,
} from '@mui/icons-material';
import { Chip } from '@mui/material';
import { alpha, type Theme, useTheme } from '@mui/material/styles';
import { type ComponentType, type FunctionComponent } from 'react';

import { type SecurityPlatform } from '../utils/api-types';
import { useFormatter } from './i18n';

// Sourced from the generated API types so new platform types added
// server-side surface as a compile error here instead of drifting.
type SecurityPlatformType = SecurityPlatform['security_platform_type'];

interface TypeConfig {
  icon: ComponentType<{ sx?: object }>;
  label: string;
  color: (theme: Theme) => string;
}

// One icon + humanized label + accent color per security platform type, so the
// raw enum (e.g. LLM_FIREWALL) never leaks into the UI. Colors are theme-aware.
const TYPE_CONFIG: Record<SecurityPlatformType, TypeConfig> = {
  EDR: {
    icon: DevicesOtherOutlined,
    label: 'EDR',
    color: theme => theme.palette.primary.main,
  },
  XDR: {
    icon: HubOutlined,
    label: 'XDR',
    color: theme => theme.palette.secondary.main,
  },
  SIEM: {
    icon: StorageOutlined,
    label: 'SIEM',
    color: theme => theme.palette.info?.main ?? theme.palette.primary.main,
  },
  SOAR: {
    icon: BoltOutlined,
    label: 'SOAR',
    color: theme => theme.palette.warning.main,
  },
  NDR: {
    icon: LanOutlined,
    label: 'NDR',
    color: theme => theme.palette.success.main,
  },
  ISPM: {
    icon: FingerprintOutlined,
    label: 'ISPM',
    color: theme => theme.palette.secondary.main,
  },
  LLM_FIREWALL: {
    icon: SecurityOutlined,
    label: 'LLM Firewall',
    color: theme => theme.palette.ai.main,
  },
  AI_GATEWAY: {
    icon: SmartToyOutlined,
    label: 'AI Gateway',
    color: theme => theme.palette.ai.main,
  },
};

interface Props {
  type?: string;
  size?: 'small' | 'medium';
}

// A "tile" chip for a security platform type: left icon + translated label,
// tinted with the type's accent color. Falls back gracefully for unknown types.
const ItemSecurityPlatformType: FunctionComponent<Props> = ({ type, size = 'small' }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const config = type ? TYPE_CONFIG[type as SecurityPlatformType] : undefined;
  const Icon = config?.icon ?? HelpOutlineOutlined;
  const accent = config ? config.color(theme) : theme.palette.text.secondary;
  const label = config ? config.label : (type ?? '-');

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
      label={t(label)}
      sx={{
        'height': 24,
        'fontSize': 11,
        'fontWeight': 600,
        'letterSpacing': '0.03em',
        'textTransform': 'uppercase',
        'borderRadius': 1,
        'color': accent,
        'borderColor': alpha(accent, 0.4),
        'backgroundColor': alpha(accent, 0.08),
        '& .MuiChip-icon': { marginLeft: 0.5 },
      }}
    />
  );
};

export default ItemSecurityPlatformType;
