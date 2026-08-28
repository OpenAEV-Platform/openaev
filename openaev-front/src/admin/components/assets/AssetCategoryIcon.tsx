import { CategoryOutlined } from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { type FunctionComponent } from 'react';

import { type CredentialOutput } from '../../../utils/api-types';
import { type AssetCategory } from './asset-categories';
import ASSET_CATEGORY_ICONS from './assetCategoryIcons';
import CREDENTIAL_CATEGORY_ICONS from './credentialCategoryIcons';

type CredentialCategory = NonNullable<CredentialOutput['credential_type']>;

interface Props extends SvgIconProps {
  category?: AssetCategory | CredentialCategory | null;
  scope?: 'asset' | 'credential';
}

const AssetCategoryIcon: FunctionComponent<Props> = ({ category, scope = 'asset', ...props }) => {
  const iconsByScope = scope === 'credential' ? CREDENTIAL_CATEGORY_ICONS : ASSET_CATEGORY_ICONS;
  const Icon = (category && iconsByScope[category]) || CategoryOutlined;
  return <Icon {...props} />;
};

export default AssetCategoryIcon;
