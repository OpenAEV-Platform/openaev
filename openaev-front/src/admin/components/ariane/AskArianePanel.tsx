import '@filigran/chatbot/styles.css';

import { type ChatMode, ChatPanel } from '@filigran/chatbot';
import { Alert, SvgIcon } from '@mui/material';
import type { Theme } from '@mui/material/styles';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import type React from 'react';
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';

interface AskArianePanelProps {
  onClose: () => void;
  onWidthChange?: (width: number) => void;
  onResizeStart?: () => void;
  onResizeEnd?: () => void;
}

type AgentFetchState = 'loading' | 'success' | 'no_agents' | 'error';

const AskArianePanel: React.FC<AskArianePanelProps> = ({
  onClose,
  onWidthChange,
  onResizeStart,
  onResizeEnd,
}) => {
  const theme = useTheme<Theme>();
  const { t } = useFormatter();
  const { me, settings } = useAuth();
  const [mode, setMode] = useState<ChatMode>('sidebar');
  const [container, setContainer] = useState<HTMLDivElement | null>(null);
  const [agentFetchState, setAgentFetchState] = useState<AgentFetchState>('loading');

  const topOffset = 64;
  const firstName = me.user_email?.split('@')[0] ?? 'User';
  const accentColor = theme.palette.ai?.main ?? '#B286FF';
  const xtmOneUrl = settings.platform_xtm_one_url || '';
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

  useEffect(() => {
    fetch('/api/xtmone/chat/agents')
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

  return createPortal(
    <ChatPanel
      mode={mode}
      onClose={onClose}
      onModeChange={setMode}
      topOffset={topOffset}
      backendType="rest"
      apiBaseUrl="/api/xtmone/chat"
      apiEndpoints={{
        agents: '/agents',
        messages: '/messages',
        sessions: '/sessions',
      }}
      user={{ firstName }}
      t={t}
      accentColor={accentColor}
      logoIcon={logoIcon}
      agentDashboardUrl={xtmOneUrl || undefined}
      promptSuggestions={promptSuggestions}
      resizable={mode === 'sidebar'}
      onWidthChange={onWidthChange}
      onResizeStart={onResizeStart}
      onResizeEnd={onResizeEnd}
    />,
    container,
  );
};

export default AskArianePanel;
