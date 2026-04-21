import '@filigran/chatbot/styles.css';

import { type ChatMode, ChatPanel } from '@filigran/chatbot';
import { SvgIcon } from '@mui/material';
import type { Theme } from '@mui/material/styles';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import type React from 'react';
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';

interface AskArianePanelProps {
  mode: ChatMode;
  onClose: () => void;
  onModeChange: (mode: ChatMode) => void;
  onWidthChange?: (width: number) => void;
  onResizeStart?: () => void;
  onResizeEnd?: () => void;
}

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
  const { me, settings } = useAuth();
  const [container, setContainer] = useState<HTMLDivElement | null>(null);

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

  return createPortal(
    <ChatPanel
      mode={mode}
      onClose={onClose}
      onModeChange={onModeChange}
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
