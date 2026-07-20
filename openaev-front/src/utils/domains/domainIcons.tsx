import { CloudOutlined, GroupsOutlined, HelpOutline, ImportantDevicesOutlined, LockOutlined, MailOutline, PublicOutlined, SmartToyOutlined, StorageOutlined, WebAssetOutlined } from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { type ComponentType, type CSSProperties, type ReactElement } from 'react';

// Shared icon + display-order mapping for security domains, usable from both
// shared components (ItemDomains) and admin modules (widgets, pickers) without
// pulling shared code into the admin dependency graph.

export interface DomainConfig {
  icon: ComponentType<SvgIconProps>;
  order: number;
}

const DOMAIN_CONFIG: Record<string, DomainConfig> = {
  'Endpoint': {
    icon: ImportantDevicesOutlined,
    order: 0,
  },
  'Network': {
    icon: PublicOutlined,
    order: 1,
  },
  'Web App': {
    icon: WebAssetOutlined,
    order: 2,
  },
  'E-mail Infiltration': {
    icon: MailOutline,
    order: 3,
  },
  'Data Exfiltration': {
    icon: StorageOutlined,
    order: 4,
  },
  'URL Filtering': {
    icon: LockOutlined,
    order: 5,
  },
  'Cloud': {
    icon: CloudOutlined,
    order: 6,
  },
  'Artificial Intelligence': {
    icon: SmartToyOutlined,
    order: 7,
  },
  'Tabletop': {
    icon: GroupsOutlined,
    order: 8,
  },
};

const DEFAULT_CONFIG: DomainConfig = {
  icon: HelpOutline,
  order: 9,
};

export const getDomainConfig = (name: string | undefined): DomainConfig => {
  return DOMAIN_CONFIG[name ?? ''] ?? DEFAULT_CONFIG;
};

export const getIconByDomain = (
  name: string | undefined,
  style: CSSProperties = {},
): ReactElement => {
  const { icon: IconComponent } = getDomainConfig(name);
  return <IconComponent fontSize="large" style={style} />;
};

export const getOrderByDomain = (name: string | undefined): number => {
  return getDomainConfig(name).order;
};
