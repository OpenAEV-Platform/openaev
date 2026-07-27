import { RadarOutlined } from '@mui/icons-material';
import { IconButton, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import { toHttpUrl } from '../../../utils/url-helper';

/**
 * Top-bar shortcut to the XTM One CTEM Command Center (the cross-product exposure
 * posture dashboard / XTM One home). Opens the XTM One URL in a new tab.
 *
 * Shown only when XTM One is connected properly (`platform_xtm_one_configured`
 * with `platform_xtm_one_url` set, guarded by the shared http(s)-only helper)
 * and the agentic AI is not disabled. NOT Enterprise-gated: the CTEM Command
 * Center is also available in full CE (metrics only).
 */
const CtemCommandCenterButton = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { settings } = useAuth();

  const xtmOneUrl = toHttpUrl(settings.platform_xtm_one_url);
  if (
    settings.filigran_chatbot_ai_cgu_status === 'disabled'
    || settings.platform_xtm_one_configured !== true
    || !xtmOneUrl
  ) {
    return null;
  }

  return (
    <Tooltip title={t('Open CTEM Command Center in XTM One')}>
      <IconButton
        size="medium"
        component="a"
        href={xtmOneUrl}
        target="_blank"
        rel="noopener noreferrer"
        sx={{
          // Same 36px squared anatomy as the other top bar icon buttons, but
          // painted with the AI purple: this shortcut belongs to XTM One
          // (agentic AI), like the Ask Ariane button next to it.
          'width': 36,
          'height': 36,
          'borderRadius': 1,
          'color': theme.palette.ai.main,
          '&:hover': { backgroundColor: alpha(theme.palette.ai.main, 0.15) },
        }}
        aria-label={t('CTEM Command Center')}
      >
        <RadarOutlined fontSize="medium" />
      </IconButton>
    </Tooltip>
  );
};

export default CtemCommandCenterButton;
