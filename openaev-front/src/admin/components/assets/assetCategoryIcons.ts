import {
  AppsOutlined,
  BadgeOutlined,
  CategoryOutlined,
  CloudOutlined,
  DnsOutlined,
  LanguageOutlined,
  RouterOutlined,
  SecurityOutlined,
  SensorsOutlined,
  SmartphoneOutlined,
  SmartToyOutlined,
  ViewInArOutlined,
} from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { type ComponentType } from 'react';

// One taxonomy glyph per asset category (level-1). Kept in a plain module (no
// component export) so icon maps (e.g. dashboard list widgets) can resolve a
// stable ComponentType per category without breaking react fast refresh.
const ASSET_CATEGORY_ICONS: Record<string, ComponentType<SvgIconProps>> = {
  HOST: DnsOutlined,
  CONTAINER_WORKLOAD: ViewInArOutlined,
  CLOUD_RESOURCE: CloudOutlined,
  WEB_APPLICATION: LanguageOutlined,
  NETWORK_DEVICE: RouterOutlined,
  MOBILE_DEVICE: SmartphoneOutlined,
  IOT_OT_DEVICE: SensorsOutlined,
  IDENTITY: BadgeOutlined,
  SAAS_APPLICATION: AppsOutlined,
  AI_TARGET: SmartToyOutlined,
  SECURITY_PLATFORM: SecurityOutlined,
  GENERIC_ASSET: CategoryOutlined,
};

export default ASSET_CATEGORY_ICONS;
