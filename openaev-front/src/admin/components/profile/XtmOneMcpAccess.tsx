import { ContentCopyOutlined, OpenInNewOutlined } from '@mui/icons-material';
import { Button, IconButton, Tooltip, Typography } from '@mui/material';

import { SECTION_LABEL_SX } from '../../../components/common/detail/EntityDetailCommon';
import Paper from '../../../components/common/Paper';
import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import { toHttpUrl } from '../../../utils/url-helper';
import { copyToClipboard } from '../../../utils/utils';

/**
 * "XTM One MCP server" profile card - shown only when the platform is
 * connected to XTM One (`platform_xtm_one_configured` + `platform_xtm_one_url`,
 * the same gate as the top-bar CTEM Command Center button).
 *
 * XTM One natively embeds an MCP (Model Context Protocol) server for every
 * registered platform: AI clients (Cursor, Claude Desktop, custom agents)
 * connect to `{platform_xtm_one_url}/mcp/openaev` with a personal XTM One
 * API key and work with OpenAEV content under the caller's own identity.
 * This card makes that endpoint discoverable from the user's profile, next
 * to the classic API access card.
 */
const XtmOneMcpAccess = () => {
  const { t } = useFormatter();
  const { settings } = useAuth();

  const xtmOneUrl = toHttpUrl(settings.platform_xtm_one_url)?.replace(/\/+$/, '');
  if (settings.platform_xtm_one_configured !== true || !xtmOneUrl) {
    return null;
  }

  const mcpEndpointUrl = `${xtmOneUrl}/mcp/openaev`;
  const xtmOneProfileUrl = `${xtmOneUrl}/profile/mcp`;

  return (
    <Paper>
      <Typography variant="h1" style={{ marginBottom: 20 }}>
        {t('XTM One MCP server')}
      </Typography>
      <Typography variant="body1">
        {t('This platform is connected to XTM One, which natively exposes an MCP (Model Context Protocol) server for OpenAEV. AI clients such as Cursor or Claude Desktop can work with scenarios, simulations, payloads and findings with your own permissions.')}
      </Typography>
      <Typography
        gutterBottom
        sx={{
          ...SECTION_LABEL_SX,
          mt: 2.5,
        }}
      >
        {t('MCP endpoint URL')}
      </Typography>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
      }}
      >
        <pre style={{
          flex: 1,
          margin: 0,
        }}
        >
          {mcpEndpointUrl}
        </pre>
        <Tooltip title={t('Copy MCP endpoint URL')}>
          <IconButton
            size="small"
            aria-label={t('Copy MCP endpoint URL')}
            onClick={() => copyToClipboard(t, mcpEndpointUrl)}
          >
            <ContentCopyOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      </div>
      <Typography variant="body2" style={{ marginTop: 20 }}>
        {t('Authenticate with a personal XTM One API key passed as a bearer token. Your endpoint, connection status and ready-to-copy client configuration are available in your XTM One profile.')}
      </Typography>
      <Button
        variant="contained"
        color="primary"
        component="a"
        href={xtmOneProfileUrl}
        target="_blank"
        rel="noopener noreferrer"
        endIcon={<OpenInNewOutlined />}
        style={{ marginTop: 20 }}
      >
        {t('Manage in XTM One')}
      </Button>
    </Paper>
  );
};

export default XtmOneMcpAccess;
