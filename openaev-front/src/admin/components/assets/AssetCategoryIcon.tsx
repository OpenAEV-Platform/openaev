import { CategoryOutlined } from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { type FunctionComponent } from 'react';

import { type AssetCategory } from './asset-categories';
import ASSET_CATEGORY_ICONS from './assetCategoryIcons';

interface Props extends SvgIconProps { category?: AssetCategory | null }

const AssetCategoryIcon: FunctionComponent<Props> = ({ category, ...props }) => {
  const Icon = (category && ASSET_CATEGORY_ICONS[category]) || CategoryOutlined;
  return <Icon {...props} />;
};

export default AssetCategoryIcon;
