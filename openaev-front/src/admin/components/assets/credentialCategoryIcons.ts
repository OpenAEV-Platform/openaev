import { VpnKeyOutlined } from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { Aws, MicrosoftAzure } from 'mdi-material-ui';
import { type ComponentType } from 'react';

const CREDENTIAL_CATEGORY_ICONS: Record<string, ComponentType<SvgIconProps>> = {
  IDENTITY: VpnKeyOutlined,
  CLOUD_AWS: Aws,
  CLOUD_AZURE: MicrosoftAzure,
  CLOUD_GCP: GoogleCloud,
};

export default CREDENTIAL_CATEGORY_ICONS;
