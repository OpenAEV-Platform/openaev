import { RadarOutlined } from '@mui/icons-material';
import { IconButton, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';

/**
 * Top-bar shortcut to the XTM One CTEM Command Center (the cross-product exposure
 * posture dashboard / XTM One home). Opens the XTM One URL in a new tab.
 *
 * Shown only when XTM One is connected properly (`platform_xtm_one_configured`
 * with `platform_xtm_one_url` set) and the agentic AI is not disabled. NOT
 * Enterprise-gated: the CTEM Command Center is also available in full CE
 * (metrics only).
 */
/**
 * Returns the value only when it is a syntactically valid http(s) URL,
 * otherwise undefined. Guards against a misconfigured (or otherwise
 * unexpected) `platform_xtm_one_url` - e.g. a `javascript:` scheme - ever
 * reaching the anchor href.
 */
const toHttpUrl = (value: string | undefined): string | undefined => {
  if (!value) {
    return undefined;
  }
  try {
    const { protocol } = new URL(value);
    return protocol === 'http:' || protocol === 'https:' ? value : undefined;
  } catch {
    return undefined;
  }
};

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
        sx={{ color: theme.palette.ai.main }}
        aria-label={t('CTEM Command Center')}
      >
        <RadarOutlined fontSize="medium" />
      </IconButton>
    </Tooltip>
  );
};

export default CtemCommandCenterButton;
