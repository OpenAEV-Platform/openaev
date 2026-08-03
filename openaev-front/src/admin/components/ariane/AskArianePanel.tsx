import { type ChatMode, ChatPanel } from '@filigran/chatbot';
import { Alert, SvgIcon } from '@mui/material';
import type { Theme } from '@mui/material/styles';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import type React from 'react';
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { useLocation } from 'react-router';

import { useFormatter } from '../../../components/i18n';
import { api } from '../../../network';
import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import { MESSAGING$ } from '../../../utils/Environment';
import useAuth from '../../../utils/hooks/useAuth';
import { toHttpUrl } from '../../../utils/url-helper';
import installChatbotCsrf from './installChatbotCsrf';

interface AskArianePanelProps {
  mode: ChatMode;
  onClose: () => void;
  onModeChange: (mode: ChatMode) => void;
  onWidthChange?: (width: number) => void;
  onResizeStart?: () => void;
  onResizeEnd?: () => void;
}

type AgentFetchState = 'loading' | 'success' | 'no_agents' | 'error';

const AskArianePanel: React.FC<AskArianePanelProps> = ({
  mode,
  onClose,
  onModeChange,
  onWidthChange,
  onResizeStart,
  onResizeEnd,
}) => {
  const theme = useTheme<Theme>();
  const { t } = useFormatter();
  const location = useLocation();
  const { me, settings } = useAuth();
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const [agentFetchState, setAgentFetchState] = useState<AgentFetchState>('loading');

  // Sit flush under the app bar, pushed down by any active top banner (EE trial,
  // system messages) so the panel never slides under the header when a banner
  // is shown.
  const { bannerHeightNumber } = computeBannerSettings(settings);
  const topOffset = 64 + bannerHeightNumber;
  const firstName = me.user_email?.split('@')[0] ?? 'User';
  const accentColor = theme.palette.ai?.main ?? '#B286FF';
  // Guarded by the shared http(s)-only helper: the URL is forwarded to the
  // chatbot widget as `agentDashboardUrl` (an anchor href), so a misconfigured
  // scheme must never reach it.
  const xtmOneUrl = toHttpUrl(settings.platform_xtm_one_url) || '';
  const isDarkMode = theme.palette.mode === 'dark';

  const logoIcon = (
    <SvgIcon
      component={LogoXtmOneIcon}
      inheritViewBox
      sx={{
        fontSize: 'inherit',
        color: 'inherit',
      }}
    />
  );

  const promptSuggestions = [
    t('Help me create a new simulation scenario'),
    t('What are the latest attack patterns?'),
    t('How do I configure detection rules?'),
    t('Summarize my recent findings'),
  ];

  // Forward the user's current in-app location so the agent is always aware
  // of the page (URI) the question is being asked from, e.g.
  // `/admin/atomic_testings/<id>`. Only the pathname is sent — the query
  // string is intentionally omitted to avoid forwarding UI state (filters,
  // view settings, …) that would bloat the payload and could leak more than
  // the agent needs. The shape is extensible — more context (page title,
  // selected entity, etc.) can be added later.
  const pageContext = { url: location.pathname };

  useEffect(() => {
    installChatbotCsrf();
    // Bootstrap the Spring Security XSRF-TOKEN cookie before the chatbot
    // widget fires its first mutating request, so installChatbotCsrf can
    // inject the X-XSRF-TOKEN header.
    api().get('/csrf').catch(() => undefined).finally(() => {
      // `credentials: 'include'` keeps this call consistent with the `withCredentials: true`
      // axios instance and avoids 401/403 in cross-origin deployments.
      fetch('/api/xtmone/chat/agents', { credentials: 'include' })
        .then((response) => {
          if (response.ok) {
            setAgentFetchState('success');
          } else if (response.status === 404) {
            setAgentFetchState('no_agents');
          } else {
            setAgentFetchState('error');
          }
        })
        .catch(() => {
          setAgentFetchState('error');
        });
    });
  }, []);

  useEffect(() => {
    const div = document.createElement('div');
    div.id = 'ask-ariane-portal';
    div.className = isDarkMode ? 'dark' : '';
    document.body.appendChild(div);
    setContainer(div);
    return () => {
      document.body.removeChild(div);
    };
  }, []);

  useEffect(() => {
    if (container) {
      container.className = isDarkMode ? 'dark' : '';
    }
  }, [isDarkMode, container]);

  if (!container) {
    return null;
  }

  if (agentFetchState === 'error' || agentFetchState === 'no_agents') {
    const severity = agentFetchState === 'no_agents' ? 'info' : 'error';
    const message = agentFetchState === 'no_agents'
      ? t('No AI assistant agents are available at the moment.')
      : t('The AI assistant service is currently unavailable. Please try again later.');
    return createPortal(
      <div
        style={{
          position: 'fixed',
          right: 0,
          top: topOffset,
          bottom: 0,
          width: 400,
          zIndex: 1200,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: theme.palette.background.paper,
          borderLeft: `1px solid ${theme.palette.divider}`,
        }}
      >
        <Alert severity={severity} sx={{ m: 2 }}>
          {message}
        </Alert>
      </div>,
      container,
    );
  }

  if (agentFetchState === 'loading') {
    return null;
  }

  const chatPanelProps = {
    mode,
    onClose,
    onModeChange,
    topOffset,
    backendType: 'rest' as const,
    apiBaseUrl: '/api/xtmone/chat',
    apiEndpoints: {
      agents: '/agents',
      messages: '/messages',
      // Mid-run steering — must be set explicitly because the chatbot
      // default ('/chat/messages/steer') assumes XTM One-style paths,
      // while the OpenAEV proxy exposes '/messages/steer' relative to
      // its '/api/xtmone/chat' base.
      steer: '/messages/steer',
      sessions: '/sessions',
      upload: '/upload',
      download: '/files',
    },
    user: { firstName },
    disableFileManagement: false,
    t,
    accentColor,
    logoIcon,
    agentDashboardUrl: xtmOneUrl || undefined,
    promptSuggestions,
    pageContext,
    resizable: mode === 'sidebar',
    onWidthChange,
    onResizeStart,
    onResizeEnd,
    onTaskComplete: (_title: string, body: string) => MESSAGING$.notifySuccess(body),
  };

  return createPortal(
    <ChatPanel {...chatPanelProps} />,
    container,
  );
};

export default AskArianePanel;
