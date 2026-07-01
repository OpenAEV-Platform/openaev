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
import { type ComponentType, type FunctionComponent } from 'react';

import { type AssetCategory } from './asset-categories';

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

interface Props extends SvgIconProps { category?: AssetCategory | null }

const AssetCategoryIcon: FunctionComponent<Props> = ({ category, ...props }) => {
  const Icon = (category && ASSET_CATEGORY_ICONS[category]) || CategoryOutlined;
  return <Icon {...props} />;
};

export default AssetCategoryIcon;
