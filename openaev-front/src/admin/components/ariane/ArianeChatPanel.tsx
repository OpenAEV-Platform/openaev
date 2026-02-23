import {
  CloseOutlined,
  CropFreeOutlined,
  EditNoteOutlined,
  ExpandMoreOutlined,
  FullscreenExitOutlined,
  LaunchOutlined,
  OpenInNewOutlined,
  PersonAddOutlined,
  PictureInPictureAltOutlined,
  SendOutlined,
  ViewSidebarOutlined,
} from '@mui/icons-material';
import {
  Avatar,
  Box,
  Button,
  ClickAwayListener,
  Divider,
  IconButton,
  InputBase,
  List,
  ListItemAvatar,
  ListItemButton,
  ListItemText,
  Paper,
  Popper,
  SvgIcon,
  Tooltip,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import { type FunctionComponent, type KeyboardEvent, useEffect, useRef, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type ArianeChatMode } from '../../../utils/Environment';
import useAuth from '../../../utils/hooks/useAuth';

interface ArianeChatPanelProps {
  mode: ArianeChatMode;
  onClose: () => void;
  onModeChange: (mode: ArianeChatMode) => void;
  bannerHeight: number;
}

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

const SIDEBAR_WIDTH = 400;
const FLOATING_WIDTH = 380;
const FLOATING_HEIGHT = 560;

const MOCK_AGENTS = [
  {
    id: 'general',
    name: 'Ariane',
    slug: 'ariane',
    icon: null,
    description: 'OpenAEV General Assistant',
  },
  {
    id: 'scenario',
    name: 'Scenario Expert',
    slug: 'scenario-expert',
    icon: '🎯',
    description: 'Attack scenario guidance',
  },
  {
    id: 'payload',
    name: 'Payload Analyst',
    slug: 'payload-analyst',
    icon: '🔬',
    description: 'Payload detection & remediation',
  },
];

const PROMPT_SUGGESTIONS = [
  'Help me create a new simulation scenario',
  'What are the latest attack patterns?',
  'How do I configure detection rules?',
  'Summarize my recent findings',
];

const ArianeChatPanel: FunctionComponent<ArianeChatPanelProps> = ({
  mode,
  onClose,
  onModeChange,
  bannerHeight,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { me, settings } = useAuth();
  const xtmOneUrl = settings.platform_xtm_one_url || '';

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [selectedAgent, setSelectedAgent] = useState(MOCK_AGENTS[0]);
  const [agentMenuOpen, setAgentMenuOpen] = useState(false);
  const [modeMenuOpen, setModeMenuOpen] = useState(false);
  const agentAnchorRef = useRef<HTMLButtonElement>(null);
  const modeAnchorRef = useRef<HTMLButtonElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const firstName = me?.user_firstname || me?.user_email?.split('@')[0] || 'there';

  const handleSendMessage = async () => {
    if (!inputValue.trim() || isLoading) return;
    const content = inputValue.trim();

    const userMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content,
      timestamp: new Date(),
    };
    setMessages(prev => [...prev, userMsg]);
    setInputValue('');
    setIsLoading(true);

    const assistantId = crypto.randomUUID();
    setMessages(prev => [...prev, {
      id: assistantId,
      role: 'assistant',
      content: '',
      timestamp: new Date(),
    }]);

    try {
      const res = await fetch('/api/xtmone/chat/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          content,
          conversation_id: conversationId,
          agent_slug: selectedAgent.slug,
        }),
      });

      if (!res.ok || !res.body) {
        setMessages(prev => prev.map(m => (m.id === assistantId
          ? {
              ...m,
              content: t('Unable to connect to XTM One. Please check the configuration.'),
            }
          : m)));
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let accumulated = '';

      // eslint-disable-next-line no-constant-condition
      while (true) {
        // eslint-disable-next-line no-await-in-loop
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
          if (!line.startsWith('data: ')) continue;
          try {
            const evt = JSON.parse(line.slice(6));
            if (evt.type === 'stream') {
              accumulated += evt.content;
              setMessages(prev => prev.map(m => (m.id === assistantId
                ? {
                    ...m,
                    content: accumulated,
                  }
                : m)));
            } else if (evt.type === 'done') {
              if (evt.conversation_id) {
                setConversationId(evt.conversation_id);
              }
              setMessages(prev => prev.map(m => (m.id === assistantId
                ? {
                    ...m,
                    content: evt.content,
                  }
                : m)));
            }
          } catch { /* skip malformed SSE lines */ }
        }
      }
      if (accumulated && !messages.find(m => m.id === assistantId)?.content) {
        setMessages(prev => prev.map(m => (m.id === assistantId
          ? {
              ...m,
              content: accumulated || 'No response.',
            }
          : m)));
      }
    } catch {
      setMessages(prev => prev.map(m => (m.id === assistantId
        ? {
            ...m,
            content: t('Sorry, an error occurred. Please try again.'),
          }
        : m)));
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const handlePromptClick = (prompt: string) => {
    setInputValue(prompt);
  };

  const handleNewChat = () => {
    setMessages([]);
    setInputValue('');
    setConversationId(null);
  };

  const toolbarHeight = (() => {
    const mixin = theme.mixins.toolbar;
    const mediaKey = Object.keys(mixin).find(k => k.includes('min-width:600px'));
    if (mediaKey) {
      const nested = mixin[mediaKey] as Record<string, unknown>;
      if (typeof nested?.minHeight === 'number') return nested.minHeight;
    }
    return (typeof mixin.minHeight === 'number' ? mixin.minHeight : 64);
  })();
  const topOffset = toolbarHeight + bannerHeight;

  const containerSx = (() => {
    switch (mode) {
      case 'sidebar':
        return {
          position: 'fixed' as const,
          top: topOffset,
          right: 0,
          bottom: 0,
          width: SIDEBAR_WIDTH,
          zIndex: theme.zIndex.drawer,
          display: 'flex',
          flexDirection: 'column' as const,
          backgroundColor: theme.palette.background.paper,
          borderLeft: `1px solid ${theme.palette.divider}`,
        };
      case 'floating':
        return {
          position: 'fixed' as const,
          bottom: 20,
          right: 20,
          width: FLOATING_WIDTH,
          height: FLOATING_HEIGHT,
          zIndex: theme.zIndex.modal,
          display: 'flex',
          flexDirection: 'column' as const,
          backgroundColor: theme.palette.background.paper,
          borderRadius: '12px',
          boxShadow: `0 8px 32px rgba(0,0,0,0.4), 0 0 0 1px ${theme.palette.divider}`,
          overflow: 'hidden',
        };
      case 'fullscreen':
        return {
          position: 'fixed' as const,
          top: topOffset,
          right: 0,
          bottom: 0,
          left: 0,
          zIndex: theme.zIndex.modal + 1,
          display: 'flex',
          flexDirection: 'column' as const,
          backgroundColor: theme.palette.background.default,
        };
      default:
        return {};
    }
  })();

  const modeOptions: {
    mode: ArianeChatMode;
    icon: typeof ViewSidebarOutlined;
    label: string;
  }[] = [
    {
      mode: 'floating',
      icon: PictureInPictureAltOutlined,
      label: t('Floating'),
    },
    {
      mode: 'sidebar',
      icon: ViewSidebarOutlined,
      label: t('Sidebar'),
    },
    {
      mode: 'fullscreen',
      icon: CropFreeOutlined,
      label: t('Full screen'),
    },
  ];

  const renderHeader = () => (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        padding: '8px 12px',
        minHeight: 48,
        borderBottom: `1px solid ${theme.palette.divider}`,
        background: `linear-gradient(135deg, ${theme.palette.ai.dark}22, ${theme.palette.ai.main}11)`,
      }}
    >
      {/* Agent switcher */}
      <Button
        ref={agentAnchorRef}
        onClick={() => setAgentMenuOpen(prev => !prev)}
        size="small"
        endIcon={<ExpandMoreOutlined sx={{ fontSize: '16px !important' }} />}
        startIcon={(
          <SvgIcon
            component={LogoXtmOneIcon}
            inheritViewBox
            sx={{
              fontSize: '18px !important',
              color: theme.palette.ai.main,
            }}
          />
        )}
        sx={{
          'textTransform': 'none',
          'color': theme.palette.text?.primary,
          'fontWeight': 600,
          'fontSize': '0.875rem',
          'padding': '4px 8px',
          'borderRadius': '8px',
          '&:hover': { backgroundColor: theme.palette.action.hover },
        }}
      >
        {selectedAgent.name}
      </Button>
      <Popper
        open={agentMenuOpen}
        anchorEl={agentAnchorRef.current}
        placement="bottom-start"
        style={{ zIndex: theme.zIndex.modal + 10 }}
      >
        <ClickAwayListener onClickAway={() => setAgentMenuOpen(false)}>
          <Paper
            elevation={8}
            sx={{
              width: 280,
              mt: 0.5,
              borderRadius: '10px',
              overflow: 'hidden',
              border: `1px solid ${theme.palette.divider}`,
            }}
          >
            <Typography
              variant="overline"
              sx={{
                px: 2,
                pt: 1.5,
                pb: 0.5,
                display: 'block',
                fontSize: '0.68rem',
                letterSpacing: 1,
              }}
            >
              {t('Switch to another agent')}
            </Typography>
            <List dense disablePadding>
              {MOCK_AGENTS.map(agent => (
                <ListItemButton
                  key={agent.id}
                  selected={agent.id === selectedAgent.id}
                  onClick={() => {
                    setSelectedAgent(agent);
                    setAgentMenuOpen(false);
                    handleNewChat();
                  }}
                  sx={{
                    'px': 2,
                    'py': 0.75,
                    '&.Mui-selected': { backgroundColor: theme.palette.ai.main + '1A' },
                  }}
                >
                  <ListItemAvatar sx={{ minWidth: 36 }}>
                    <Avatar
                      sx={{
                        width: 28,
                        height: 28,
                        fontSize: '0.85rem',
                        bgcolor: agent.icon ? 'transparent' : theme.palette.ai.dark,
                      }}
                    >
                      {agent.icon || (
                        <SvgIcon
                          component={LogoXtmOneIcon}
                          inheritViewBox
                          sx={{
                            fontSize: 16,
                            color: theme.palette.ai.light,
                          }}
                        />
                      )}
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={agent.name}
                    secondary={agent.description}
                    primaryTypographyProps={{
                      fontSize: '0.8125rem',
                      fontWeight: 500,
                    }}
                    secondaryTypographyProps={{ fontSize: '0.7rem' }}
                  />
                </ListItemButton>
              ))}
            </List>
            <Divider />
            <List dense disablePadding>
              <ListItemButton
                sx={{
                  px: 2,
                  py: 0.75,
                }}
                onClick={() => {
                  setAgentMenuOpen(false);
                  window.open(`${xtmOneUrl}/agents`, '_blank');
                }}
              >
                <ListItemAvatar sx={{ minWidth: 36 }}>
                  <LaunchOutlined sx={{
                    fontSize: 18,
                    color: theme.palette.text?.secondary,
                  }}
                  />
                </ListItemAvatar>
                <ListItemText
                  primary={t('Browse agents')}
                  primaryTypographyProps={{ fontSize: '0.8125rem' }}
                />
              </ListItemButton>
              <ListItemButton
                sx={{
                  px: 2,
                  py: 0.75,
                }}
                onClick={() => {
                  setAgentMenuOpen(false);
                  window.open(`${xtmOneUrl}/agents/new`, '_blank');
                }}
              >
                <ListItemAvatar sx={{ minWidth: 36 }}>
                  <PersonAddOutlined sx={{
                    fontSize: 18,
                    color: theme.palette.text?.secondary,
                  }}
                  />
                </ListItemAvatar>
                <ListItemText
                  primary={t('Create agent')}
                  primaryTypographyProps={{ fontSize: '0.8125rem' }}
                />
              </ListItemButton>
            </List>
          </Paper>
        </ClickAwayListener>
      </Popper>

      <Box sx={{ flexGrow: 1 }} />

      {/* New chat */}
      <Tooltip title={t('New chat')}>
        <IconButton size="small" onClick={handleNewChat} sx={{ mr: 0.25 }}>
          <EditNoteOutlined sx={{ fontSize: 20 }} />
        </IconButton>
      </Tooltip>

      {/* Mode switcher */}
      <Tooltip title={t('Switch view')}>
        <IconButton
          ref={modeAnchorRef}
          size="small"
          onClick={() => setModeMenuOpen(prev => !prev)}
          sx={{ mr: 0.25 }}
        >
          {mode === 'sidebar' && <ViewSidebarOutlined sx={{ fontSize: 20 }} />}
          {mode === 'floating' && <PictureInPictureAltOutlined sx={{ fontSize: 20 }} />}
          {mode === 'fullscreen' && <FullscreenExitOutlined sx={{ fontSize: 20 }} />}
        </IconButton>
      </Tooltip>
      <Popper
        open={modeMenuOpen}
        anchorEl={modeAnchorRef.current}
        placement="bottom-end"
        style={{ zIndex: theme.zIndex.modal + 10 }}
      >
        <ClickAwayListener onClickAway={() => setModeMenuOpen(false)}>
          <Paper
            elevation={8}
            sx={{
              width: 180,
              mt: 0.5,
              borderRadius: '10px',
              overflow: 'hidden',
              border: `1px solid ${theme.palette.divider}`,
            }}
          >
            <Typography
              variant="overline"
              sx={{
                px: 2,
                pt: 1.5,
                pb: 0.5,
                display: 'block',
                fontSize: '0.68rem',
                letterSpacing: 1,
              }}
            >
              {t('Switch to')}
            </Typography>
            <List dense disablePadding sx={{ pb: 0.5 }}>
              {modeOptions.map(opt => (
                <ListItemButton
                  key={opt.mode}
                  selected={mode === opt.mode}
                  onClick={() => {
                    onModeChange(opt.mode);
                    setModeMenuOpen(false);
                  }}
                  sx={{
                    'px': 2,
                    'py': 0.5,
                    '&.Mui-selected': { backgroundColor: theme.palette.ai.main + '1A' },
                  }}
                >
                  <opt.icon sx={{
                    fontSize: 18,
                    mr: 1.5,
                    color: theme.palette.text?.secondary,
                  }}
                  />
                  <ListItemText
                    primary={opt.label}
                    primaryTypographyProps={{ fontSize: '0.8125rem' }}
                  />
                </ListItemButton>
              ))}
            </List>
          </Paper>
        </ClickAwayListener>
      </Popper>

      {/* More / Open in new tab */}
      <Tooltip title={t('Open in new window')}>
        <IconButton size="small" sx={{ mr: 0.25 }}>
          <OpenInNewOutlined sx={{ fontSize: 18 }} />
        </IconButton>
      </Tooltip>

      {/* Close */}
      <Tooltip title={t('Close')}>
        <IconButton size="small" onClick={onClose}>
          <CloseOutlined sx={{ fontSize: 20 }} />
        </IconButton>
      </Tooltip>
    </Box>
  );

  const renderWelcome = () => (
    <Box
      sx={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        px: 3,
        pb: 4,
      }}
    >
      <SvgIcon
        component={LogoXtmOneIcon}
        inheritViewBox
        sx={{
          fontSize: 48,
          color: theme.palette.ai.main,
          mb: 2,
          filter: `drop-shadow(0 0 12px ${theme.palette.ai.main}40)`,
        }}
      />
      <Typography
        variant="h6"
        sx={{
          fontWeight: 500,
          mb: 3,
          textAlign: 'center',
          fontFamily: '"Geologica", sans-serif',
        }}
      >
        {t('How can I help you, ')}
        {firstName}
        ?
      </Typography>

      <Box sx={{
        width: '100%',
        maxWidth: 320,
      }}
      >
        <Typography
          variant="overline"
          sx={{
            display: 'block',
            textAlign: 'center',
            mb: 1,
            fontSize: '0.65rem',
            letterSpacing: 1.5,
            color: theme.palette.ai.main,
            fontWeight: 600,
          }}
        >
          {t('Suggestions')}
        </Typography>
        {PROMPT_SUGGESTIONS.map(prompt => (
          <Button
            key={prompt}
            fullWidth
            variant="text"
            onClick={() => handlePromptClick(prompt)}
            sx={{
              'justifyContent': 'flex-start',
              'textTransform': 'none',
              'fontSize': '0.8125rem',
              'color': theme.palette.text?.primary,
              'py': 0.75,
              'px': 1.5,
              'mb': 0.5,
              'borderRadius': '8px',
              'textAlign': 'left',
              'border': `1px solid ${theme.palette.divider}`,
              '&:hover': {
                backgroundColor: theme.palette.ai.main + '0D',
                borderColor: theme.palette.ai.main + '40',
              },
            }}
          >
            {t(prompt)}
          </Button>
        ))}
      </Box>
    </Box>
  );

  const renderMessages = () => (
    <Box
      sx={{
        flex: 1,
        overflowY: 'auto',
        px: 2,
        py: 1.5,
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
      }}
    >
      {messages.map(msg => (
        <Box
          key={msg.id}
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: msg.role === 'user' ? 'flex-end' : 'flex-start',
          }}
        >
          {msg.role === 'assistant' && (
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.75,
              mb: 0.5,
            }}
            >
              <Avatar
                sx={{
                  width: 22,
                  height: 22,
                  bgcolor: theme.palette.ai.dark,
                }}
              >
                <SvgIcon
                  component={LogoXtmOneIcon}
                  inheritViewBox
                  sx={{
                    fontSize: 12,
                    color: theme.palette.ai.light,
                  }}
                />
              </Avatar>
              <Typography
                variant="body2"
                sx={{
                  fontWeight: 600,
                  fontSize: '0.75rem',
                }}
              >
                {selectedAgent.name}
              </Typography>
            </Box>
          )}
          <Box
            sx={{
              maxWidth: '85%',
              padding: '8px 14px',
              borderRadius: msg.role === 'user' ? '14px 14px 4px 14px' : '14px 14px 14px 4px',
              backgroundColor: msg.role === 'user'
                ? theme.palette.ai.dark
                : theme.palette.background.default,
              color: msg.role === 'user'
                ? theme.palette.common?.white
                : theme.palette.text?.primary,
              fontSize: '0.8125rem',
              lineHeight: 1.5,
              border: msg.role === 'assistant'
                ? `1px solid ${theme.palette.divider}`
                : 'none',
            }}
          >
            {msg.content}
          </Box>
        </Box>
      ))}
      <div ref={messagesEndRef} />
    </Box>
  );

  const renderInput = () => (
    <Box
      sx={{
        px: 2,
        py: 1.5,
        borderTop: `1px solid ${theme.palette.divider}`,
      }}
    >
      <Box
        sx={{
          'display': 'flex',
          'alignItems': 'center',
          'border': `1px solid ${theme.palette.divider}`,
          'borderRadius': '12px',
          'padding': '4px 4px 4px 14px',
          'transition': 'border-color 0.2s',
          '&:focus-within': { borderColor: theme.palette.ai.main },
        }}
      >
        <InputBase
          fullWidth
          placeholder={t('Ask, @mention, or / for actions')}
          value={inputValue}
          onChange={e => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          sx={{
            'fontSize': '0.8125rem',
            '& .MuiInputBase-input': { padding: '6px 0' },
          }}
          multiline
          maxRows={4}
        />
        <IconButton
          size="small"
          onClick={handleSendMessage}
          disabled={!inputValue.trim() || isLoading}
          sx={{
            color: inputValue.trim() && !isLoading ? theme.palette.ai.main : theme.palette.action.disabled,
            bgcolor: inputValue.trim() && !isLoading ? theme.palette.ai.main + '1A' : 'transparent',
            borderRadius: '8px',
            width: 32,
            height: 32,
          }}
        >
          <SendOutlined sx={{ fontSize: 18 }} />
        </IconButton>
      </Box>
      <Typography
        variant="body2"
        sx={{
          textAlign: 'center',
          fontSize: '0.65rem',
          color: theme.palette.text?.secondary,
          mt: 0.75,
          opacity: 0.7,
        }}
      >
        {t('Uses AI. Verify results.')}
      </Typography>
    </Box>
  );

  return (
    <Box sx={containerSx}>
      {renderHeader()}
      {messages.length === 0 ? renderWelcome() : renderMessages()}
      {renderInput()}
    </Box>
  );
};

export default ArianeChatPanel;
