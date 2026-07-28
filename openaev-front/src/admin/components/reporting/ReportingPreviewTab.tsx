import { RefreshOutlined } from '@mui/icons-material';
import { Box, IconButton, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { computeTenantBasename } from '../../../utils/url-helper';

interface Props {
  reportingId: string;
  /**
   * Changes whenever the report configuration changes (updated_at timestamp):
   * remounts the iframe so any update is reflected in the preview without a
   * manual refresh.
   */
  refreshToken?: string;
}

/**
 * Live in-app preview of the report: an iframe on the standalone chrome-less
 * render route, so the paged CSS and the scoped branding theme stay fully
 * isolated from the admin chrome. Session cookies authenticate the requests -
 * no generation token is needed here.
 */
const ReportingPreviewTab: FunctionComponent<Props> = ({ reportingId, refreshToken }) => {
  const { t } = useFormatter();
  const [previewKey, setPreviewKey] = useState(0);

  // The render route lives at the ROUTER ROOT (see root.tsx), while the app is
  // served under APP_BASE_PATH + a tenant segment: derive the absolute URL from
  // the router basename - a relative link from the current admin URL would not
  // resolve.
  const renderUrl = `${computeTenantBasename()}/reporting/${reportingId}/render`;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 1,
      marginTop: 2,
    }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
      >
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {t('Live preview of the report as it will be generated.')}
        </Typography>
        <Tooltip title={t('Refresh preview')}>
          <IconButton size="small" color="primary" onClick={() => setPreviewKey(key => key + 1)}>
            <RefreshOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>
      <Box sx={{
        border: theme => `1px solid ${theme.palette.divider}`,
        borderRadius: 1,
        overflow: 'hidden',
      }}
      >
        <iframe
          key={`${refreshToken ?? ''}-${previewKey}`}
          src={renderUrl}
          title={t('Report preview')}
          style={{
            width: '100%',
            // Roughly one A4 page (sqrt(2) aspect) at the default panel width,
            // the report scrolls inside the frame anyway.
            height: '80vh',
            border: 0,
            display: 'block',
          }}
        />
      </Box>
    </Box>
  );
};

export default ReportingPreviewTab;
