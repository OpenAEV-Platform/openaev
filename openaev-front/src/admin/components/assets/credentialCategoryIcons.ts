import { VpnKeyOutlined } from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { type ComponentType } from 'react';

const CREDENTIAL_CATEGORY_ICONS: Record<string, ComponentType<SvgIconProps>> = { IDENTITY: VpnKeyOutlined };

export default CREDENTIAL_CATEGORY_ICONS;
