import { type Theme } from '@mui/material/styles';

import { type ReportingBranding } from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';

/**
 * Resolve the logo displayed on the cover and running footer: report branding
 * document first, then the platform theme logo (theme.logo already falls back
 * to the bundled OpenAEV logo asset inside the theme builders).
 */
const resolveReportingLogo = (theme: Theme, branding?: ReportingBranding): string | undefined => {
  if (branding?.logo_document_id) {
    return buildTenantApiPath(`/api/documents/${branding.logo_document_id}/file`);
  }
  return theme.logo;
};

export default resolveReportingLogo;
