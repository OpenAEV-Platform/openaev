import {
  AttachFileOutlined,
  CloseOutlined,
  ContentCopyOutlined,
  CropFreeOutlined,
  DoneOutlined,
  EditNoteOutlined,
  ExpandMoreOutlined,
  FullscreenExitOutlined,
  InsertDriveFileOutlined,
  LaunchOutlined,
  PersonAddOutlined,
  PictureInPictureAltOutlined,
  PsychologyOutlined,
  SendOutlined,
  StopCircleOutlined,
  ViewSidebarOutlined,
} from '@mui/icons-material';
import {
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
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
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

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
  files?: ChatFile[];
}

interface ChatFile {
  name: string;
  type: string;
  size: number;
  dataUrl?: string;
}

interface XtmAgent {
  id: string;
  name: string;
  slug: string | null;
  icon: string | null;
  description: string | null;
}

const SIDEBAR_WIDTH = 400;
const FLOATING_WIDTH = 380;
const FLOATING_HEIGHT = 560;

const PROMPT_SUGGESTIONS = [
  'Help me create a new simulation scenario',
  'What are the latest attack patterns?',
  'How do I configure detection rules?',
  'Summarize my recent findings',
];

const TYPING_DOT_KEYFRAMES = `
@keyframes arianeChatDot {
  0%, 80%, 100% { opacity: 0.25; transform: scale(0.85); }
  40% { opacity: 1; transform: scale(1.15); }
}`;

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

  const STORAGE_KEY = 'arianeChatConversationId';
  const STORAGE_AGENT_KEY = 'arianeChatAgentSlug';

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(
    () => localStorage.getItem(STORAGE_KEY),
  );
  const [agents, setAgents] = useState<XtmAgent[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<XtmAgent | null>(null);
  const [agentMenuOpen, setAgentMenuOpen] = useState(false);
  const [modeMenuOpen, setModeMenuOpen] = useState(false);
  const [attachedFiles, setAttachedFiles] = useState<ChatFile[]>([]);
  const [copiedBlock, setCopiedBlock] = useState<string | null>(null);
  const agentAnchorRef = useRef<HTMLButtonElement>(null);
  const modeAnchorRef = useRef<HTMLButtonElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const historyLoadedRef = useRef(false);
  const abortControllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    fetch('/api/xtmone/chat/agents')
      .then(res => (res.ok ? res.json() : []))
      .then((data: XtmAgent[]) => {
        setAgents(data);
        if (data.length > 0 && !selectedAgent) {
          const savedSlug = localStorage.getItem(STORAGE_AGENT_KEY);
          const match = savedSlug ? data.find(a => a.slug === savedSlug) : null;
          setSelectedAgent(match || data[0]);
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!conversationId || historyLoadedRef.current || !selectedAgent) return;
    historyLoadedRef.current = true;
    fetch('/api/xtmone/chat/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        conversation_id: conversationId,
        agent_slug: selectedAgent.slug,
      }),
    })
      .then(res => (res.ok ? res.json() : null))
      .then((data) => {
        if (!data?.messages?.length) return;
        const restored: ChatMessage[] = data.messages.map(
          (m: {
            role: string;
            content: string;
          }, i: number) => ({
            id: `restored-${i}`,
            role: m.role as 'user' | 'assistant',
            content: m.content,
            timestamp: new Date(),
          }),
        );
        setMessages(restored);
      })
      .catch(() => {});
  }, [conversationId, selectedAgent]);

  const firstName = me?.user_firstname || me?.user_email?.split('@')[0] || 'there';
  const agentName = selectedAgent?.name || 'Ariane';

  const handleFileAdd = (fileList: FileList | null) => {
    if (!fileList) return;
    const newFiles: ChatFile[] = [];
    Array.from(fileList).forEach((file) => {
      const reader = new FileReader();
      reader.onload = () => {
        newFiles.push({
          name: file.name,
          type: file.type,
          size: file.size,
          dataUrl: reader.result as string,
        });
        if (newFiles.length === fileList.length) {
          setAttachedFiles(prev => [...prev, ...newFiles]);
        }
      };
      reader.readAsDataURL(file);
    });
  };

  const handlePaste = (e: React.ClipboardEvent) => {
    const { files } = e.clipboardData;
    if (files.length > 0) {
      e.preventDefault();
      handleFileAdd(files);
    }
  };

  const handleSendMessage = async () => {
    if ((!inputValue.trim() && attachedFiles.length === 0) || isLoading) return;
    const content = inputValue.trim();

    const userMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content,
      timestamp: new Date(),
      files: attachedFiles.length > 0 ? [...attachedFiles] : undefined,
    };
    setMessages(prev => [...prev, userMsg]);
    setInputValue('');
    setAttachedFiles([]);
    setIsLoading(true);

    const assistantId = crypto.randomUUID();
    setMessages(prev => [...prev, {
      id: assistantId,
      role: 'assistant',
      content: '',
      timestamp: new Date(),
    }]);

    try {
      const controller = new AbortController();
      abortControllerRef.current = controller;
      const res = await fetch('/api/xtmone/chat/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          content,
          conversation_id: conversationId,
          agent_slug: selectedAgent?.slug,
        }),
        signal: controller.signal,
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
            if (evt.type === 'error') {
              setMessages(prev => prev.map(m => (m.id === assistantId
                ? {
                    ...m,
                    content: evt.content || t('Unable to connect to XTM One. Please check the configuration.'),
                  }
                : m)));
              setIsLoading(false);
              return;
            } else if (evt.type === 'stream') {
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
                localStorage.setItem(STORAGE_KEY, evt.conversation_id);
              }
              setMessages(prev => prev.map(m => (m.id === assistantId
                ? {
                    ...m,
                    content: evt.content,
                  }
                : m)));
            }
          } catch { /* skip malformed SSE */ }
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
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      setMessages(prev => prev.map(m => (m.id === assistantId
        ? {
            ...m,
            content: t('Sorry, an error occurred. Please try again.'),
          }
        : m)));
    } finally {
      abortControllerRef.current = null;
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const handlePromptClick = (prompt: string) => setInputValue(prompt);

  const handleNewChat = () => {
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setMessages([]);
    setInputValue('');
    setConversationId(null);
    setAttachedFiles([]);
    setIsLoading(false);
    localStorage.removeItem(STORAGE_KEY);
    historyLoadedRef.current = false;
  };

  const handleSwitchAgent = (agent: XtmAgent) => {
    if (agent.id === selectedAgent?.id) {
      setAgentMenuOpen(false);
      return;
    }
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setSelectedAgent(agent);
    if (agent.slug) localStorage.setItem(STORAGE_AGENT_KEY, agent.slug);
    setAgentMenuOpen(false);
    setMessages([]);
    setInputValue('');
    setConversationId(null);
    setAttachedFiles([]);
    setIsLoading(false);
    localStorage.removeItem(STORAGE_KEY);
    historyLoadedRef.current = false;
  };

  const handleStopGenerating = () => {
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setIsLoading(false);
    setMessages(prev => prev.filter(m => !(m.role === 'assistant' && !m.content)));
  };

  const handleCopyCode = (code: string) => {
    navigator.clipboard.writeText(code);
    setCopiedBlock(code);
    setTimeout(() => setCopiedBlock(null), 2000);
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

  // -- Markdown custom renderers --
  const markdownComponents = {
    p: ({ children }: { children?: React.ReactNode }) => (
      <Typography
        component="p"
        sx={{
          'mb': 1.5,
          '&:last-child': { mb: 0 },
          'lineHeight': 1.75,
          'wordBreak': 'break-word',
          'fontSize': '0.8125rem',
        }}
      >
        {children}
      </Typography>
    ),
    code: ({ className, children }: {
      className?: string;
      children?: React.ReactNode;
    }) => {
      const match = /language-(\w+)/.exec(className || '');
      const codeStr = String(children).replace(/\n$/, '');
      if (match) {
        return (
          <Box sx={{
            my: 1.5,
            borderRadius: '8px',
            border: `1px solid ${theme.palette.divider}`,
            overflow: 'hidden',
            bgcolor: 'rgba(255,255,255,0.03)',
          }}
          >
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              px: 1.5,
              py: 0.75,
              borderBottom: `1px solid ${theme.palette.divider}`,
              bgcolor: 'rgba(255,255,255,0.03)',
            }}
            >
              <Typography sx={{
                fontSize: '0.7rem',
                color: theme.palette.text?.secondary,
                fontFamily: 'monospace',
              }}
              >
                {match[1]}
              </Typography>
              <IconButton size="small" onClick={() => handleCopyCode(codeStr)} sx={{ p: 0.25 }}>
                {copiedBlock === codeStr
                  ? (
                      <DoneOutlined sx={{
                        fontSize: 14,
                        color: theme.palette.success.main,
                      }}
                      />
                    )
                  : (
                      <ContentCopyOutlined sx={{
                        fontSize: 14,
                        color: theme.palette.text?.secondary,
                      }}
                      />
                    )}
              </IconButton>
            </Box>
            <Box
              component="pre"
              sx={{
                m: 0,
                px: 1.5,
                py: 1,
                overflowX: 'auto',
              }}
            >
              <Box
                component="code"
                sx={{
                  fontFamily: 'monospace',
                  fontSize: '0.75rem',
                  lineHeight: 1.7,
                  color: theme.palette.text?.primary,
                  whiteSpace: 'pre',
                }}
              >
                {codeStr}
              </Box>
            </Box>
          </Box>
        );
      }
      return (
        <Box
          component="code"
          sx={{
            bgcolor: 'rgba(255,255,255,0.08)',
            px: 0.75,
            py: 0.25,
            borderRadius: '4px',
            fontFamily: 'monospace',
            fontSize: '0.75rem',
            color: theme.palette.ai.main,
          }}
        >
          {children}
        </Box>
      );
    },
    ul: ({ children }: { children?: React.ReactNode }) => (
      <Box
        component="ul"
        sx={{
          'pl': 2.5,
          'mb': 1.5,
          '& li': { mb: 0.5 },
          '& li::marker': { color: theme.palette.ai.main + '80' },
          'fontSize': '0.8125rem',
        }}
      >
        {children}
      </Box>
    ),
    ol: ({ children }: { children?: React.ReactNode }) => (
      <Box
        component="ol"
        sx={{
          'pl': 2.5,
          'mb': 1.5,
          '& li': { mb: 0.5 },
          '& li::marker': { color: theme.palette.ai.main + '80' },
          'fontSize': '0.8125rem',
        }}
      >
        {children}
      </Box>
    ),
    blockquote: ({ children }: { children?: React.ReactNode }) => (
      <Box sx={{
        my: 1.5,
        borderLeft: `2px solid ${theme.palette.ai.main}50`,
        bgcolor: theme.palette.ai.main + '08',
        pl: 2,
        pr: 1.5,
        py: 1,
        borderRadius: '0 6px 6px 0',
        fontStyle: 'italic',
        color: theme.palette.text?.secondary,
      }}
      >
        {children}
      </Box>
    ),
    a: ({ href, children }: {
      href?: string;
      children?: React.ReactNode;
    }) => (
      <Box
        component="a"
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        sx={{
          'color': theme.palette.ai.main,
          'textDecoration': 'underline',
          'textUnderlineOffset': 2,
          '&:hover': { color: theme.palette.ai.light },
        }}
      >
        {children}
      </Box>
    ),
    h1: ({ children }: { children?: React.ReactNode }) => (
      <Typography
        variant="h6"
        sx={{
          'mt': 2,
          'mb': 1,
          'fontWeight': 700,
          'fontSize': '1rem',
          '&:first-of-type': { mt: 0 },
        }}
      >
        {children}
      </Typography>
    ),
    h2: ({ children }: { children?: React.ReactNode }) => (
      <Typography sx={{
        'mt': 1.5,
        'mb': 1,
        'fontWeight': 700,
        'fontSize': '0.9rem',
        '&:first-of-type': { mt: 0 },
      }}
      >
        {children}
      </Typography>
    ),
    h3: ({ children }: { children?: React.ReactNode }) => (
      <Typography sx={{
        'mt': 1.5,
        'mb': 0.75,
        'fontWeight': 600,
        'fontSize': '0.85rem',
        '&:first-of-type': { mt: 0 },
      }}
      >
        {children}
      </Typography>
    ),
    table: ({ children }: { children?: React.ReactNode }) => (
      <Box sx={{
        my: 1.5,
        overflowX: 'auto',
        borderRadius: '8px',
        border: `1px solid ${theme.palette.divider}`,
      }}
      >
        <Box
          component="table"
          sx={{
            width: '100%',
            borderCollapse: 'collapse',
            fontSize: '0.75rem',
          }}
        >
          {children}
        </Box>
      </Box>
    ),
    th: ({ children }: { children?: React.ReactNode }) => (
      <Box
        component="th"
        sx={{
          px: 1.5,
          py: 1,
          textAlign: 'left',
          fontWeight: 600,
          bgcolor: 'rgba(255,255,255,0.04)',
          borderBottom: `1px solid ${theme.palette.divider}`,
        }}
      >
        {children}
      </Box>
    ),
    td: ({ children }: { children?: React.ReactNode }) => (
      <Box
        component="td"
        sx={{
          px: 1.5,
          py: 1,
          borderBottom: `1px solid ${theme.palette.divider}`,
        }}
      >
        {children}
      </Box>
    ),
  };

  // -- Thinking indicator --
  const renderThinking = () => (
    <Box sx={{
      display: 'flex',
      gap: 1.5,
      alignItems: 'flex-start',
    }}
    >
      <Avatar sx={{
        width: 28,
        height: 28,
        bgcolor: `linear-gradient(135deg, ${theme.palette.ai.main}30, ${theme.palette.ai.main}10)`,
        background: `linear-gradient(135deg, ${theme.palette.ai.main}30, ${theme.palette.ai.main}10)`,
      }}
      >
        <SvgIcon
          component={LogoXtmOneIcon}
          inheritViewBox
          sx={{
            fontSize: 14,
            color: theme.palette.ai.main,
          }}
        />
      </Avatar>
      <Box sx={{
        borderRadius: '10px',
        bgcolor: 'rgba(255,255,255,0.03)',
        px: 2,
        py: 1.5,
        position: 'relative',
        overflow: 'hidden',
      }}
      >
        <Box sx={{
          position: 'absolute',
          inset: 0,
          background: `linear-gradient(90deg, ${theme.palette.ai.main}06, transparent, ${theme.palette.ai.main}06)`,
          animation: 'pulse 2s ease-in-out infinite',
          pointerEvents: 'none',
        }}
        />
        <Box sx={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          gap: 1.25,
        }}
        >
          <PsychologyOutlined sx={{
            fontSize: 16,
            color: theme.palette.ai.main,
            animation: 'pulse 2s ease-in-out infinite',
          }}
          />
          <Typography sx={{
            fontSize: '0.8rem',
            color: theme.palette.text?.secondary,
          }}
          >
            {t('Thinking...')}
          </Typography>
          <style>{TYPING_DOT_KEYFRAMES}</style>
          <Box sx={{
            display: 'flex',
            gap: '3px',
            alignItems: 'center',
            ml: 0.5,
          }}
          >
            {[0, 0.15, 0.3].map((delay, i) => (
              <Box
                key={String(i)}
                component="span"
                sx={{
                  height: 5,
                  width: 5,
                  borderRadius: '50%',
                  bgcolor: theme.palette.ai.main + '80',
                  animation: 'arianeChatDot 1s ease-in-out infinite',
                  animationDelay: `${delay}s`,
                }}
              />
            ))}
          </Box>
        </Box>
      </Box>
    </Box>
  );

  const renderHeader = () => (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      padding: '8px 12px',
      minHeight: 48,
      borderBottom: `1px solid ${theme.palette.divider}`,
      background: `linear-gradient(135deg, ${theme.palette.ai.dark}22, ${theme.palette.ai.main}11)`,
    }}
    >
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
        {agentName}
      </Button>
      <Popper open={agentMenuOpen} anchorEl={agentAnchorRef.current} placement="bottom-start" style={{ zIndex: theme.zIndex.modal + 10 }}>
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
            {agents.length === 0 && (
              <Box sx={{
                px: 2,
                py: 1,
              }}
              >
                <CircularProgress size={16} sx={{ color: theme.palette.ai.main }} />
              </Box>
            )}
            <List dense disablePadding>
              {agents.map(agent => (
                <ListItemButton
                  key={agent.id}
                  selected={agent.id === selectedAgent?.id}
                  onClick={() => handleSwitchAgent(agent)}
                  sx={{
                    'px': 2,
                    'py': 0.75,
                    '&.Mui-selected': { backgroundColor: theme.palette.ai.main + '1A' },
                  }}
                >
                  <ListItemAvatar sx={{ minWidth: 36 }}>
                    <Avatar sx={{
                      width: 28,
                      height: 28,
                      background: `linear-gradient(135deg, ${theme.palette.ai.main}30, ${theme.palette.ai.main}10)`,
                    }}
                    >
                      <SvgIcon
                        component={LogoXtmOneIcon}
                        inheritViewBox
                        sx={{
                          fontSize: 16,
                          color: theme.palette.ai.main,
                        }}
                      />
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
                <ListItemAvatar sx={{
                  minWidth: 36,
                  display: 'flex',
                  alignItems: 'center',
                }}
                >
                  <LaunchOutlined sx={{
                    fontSize: 18,
                    color: theme.palette.text?.secondary,
                  }}
                  />
                </ListItemAvatar>
                <ListItemText primary={t('Browse agents')} primaryTypographyProps={{ fontSize: '0.8125rem' }} />
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
                <ListItemAvatar sx={{
                  minWidth: 36,
                  display: 'flex',
                  alignItems: 'center',
                }}
                >
                  <PersonAddOutlined sx={{
                    fontSize: 18,
                    color: theme.palette.text?.secondary,
                  }}
                  />
                </ListItemAvatar>
                <ListItemText primary={t('Create agent')} primaryTypographyProps={{ fontSize: '0.8125rem' }} />
              </ListItemButton>
            </List>
          </Paper>
        </ClickAwayListener>
      </Popper>
      <Box sx={{ flexGrow: 1 }} />
      <Tooltip title={t('New chat')}><IconButton size="small" onClick={handleNewChat} sx={{ mr: 0.25 }}><EditNoteOutlined sx={{ fontSize: 20 }} /></IconButton></Tooltip>
      <Tooltip title={t('Switch view')}>
        <IconButton ref={modeAnchorRef} size="small" onClick={() => setModeMenuOpen(prev => !prev)} sx={{ mr: 0.25 }}>
          {mode === 'sidebar' && <ViewSidebarOutlined sx={{ fontSize: 20 }} />}
          {mode === 'floating' && <PictureInPictureAltOutlined sx={{ fontSize: 20 }} />}
          {mode === 'fullscreen' && <FullscreenExitOutlined sx={{ fontSize: 20 }} />}
        </IconButton>
      </Tooltip>
      <Popper open={modeMenuOpen} anchorEl={modeAnchorRef.current} placement="bottom-end" style={{ zIndex: theme.zIndex.modal + 10 }}>
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
                  <ListItemText primary={opt.label} primaryTypographyProps={{ fontSize: '0.8125rem' }} />
                </ListItemButton>
              ))}
            </List>
          </Paper>
        </ClickAwayListener>
      </Popper>
      <Tooltip title={t('Close')}><IconButton size="small" onClick={onClose}><CloseOutlined sx={{ fontSize: 20 }} /></IconButton></Tooltip>
    </Box>
  );

  const renderWelcome = () => (
    <Box sx={{
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
    <Box sx={{
      flex: 1,
      overflowY: 'auto',
      px: 2,
      py: 1.5,
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      {messages.map((msg) => {
        const isAssistant = msg.role === 'assistant';
        const isEmpty = !msg.content;
        const isThinking = isAssistant && isEmpty && isLoading;
        if (isThinking) return <Box key={msg.id}>{renderThinking()}</Box>;
        return (
          <Box
            key={msg.id}
            sx={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: isAssistant ? 'flex-start' : 'flex-end',
            }}
          >
            {isAssistant && (
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.75,
                mb: 0.5,
              }}
              >
                <Avatar sx={{
                  width: 24,
                  height: 24,
                  background: `linear-gradient(135deg, ${theme.palette.ai.main}30, ${theme.palette.ai.main}10)`,
                }}
                >
                  <SvgIcon
                    component={LogoXtmOneIcon}
                    inheritViewBox
                    sx={{
                      fontSize: 12,
                      color: theme.palette.ai.main,
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
                  {agentName}
                </Typography>
              </Box>
            )}
            {msg.files && msg.files.length > 0 && (
              <Box sx={{
                display: 'flex',
                gap: 0.75,
                flexWrap: 'wrap',
                mb: 0.75,
              }}
              >
                {msg.files.map((f, i) => (
                  <Chip
                    key={String(i)}
                    icon={<InsertDriveFileOutlined sx={{ fontSize: 14 }} />}
                    label={f.name}
                    size="small"
                    variant="outlined"
                    sx={{
                      fontSize: '0.7rem',
                      borderColor: theme.palette.divider,
                    }}
                  />
                ))}
              </Box>
            )}
            <Box sx={{
              maxWidth: '90%',
              ...(isAssistant
                ? {
                    pl: 0.5,
                    py: 0.5,
                    fontSize: '0.8125rem',
                    lineHeight: 1.75,
                  }
                : {
                    padding: '8px 14px',
                    borderRadius: '14px 14px 4px 14px',
                    bgcolor: theme.palette.ai.dark,
                    color: theme.palette.common?.white,
                    fontSize: '0.8125rem',
                    lineHeight: 1.5,
                  }),
            }}
            >
              {isAssistant
                ? (
                    <Markdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
                      {msg.content}
                    </Markdown>
                  )
                : msg.content}
              {isAssistant && isEmpty && !isLoading && (
                <Typography sx={{
                  fontSize: '0.8125rem',
                  color: theme.palette.text?.secondary,
                  fontStyle: 'italic',
                }}
                >
                  ...
                </Typography>
              )}
              {isAssistant && !isEmpty && isLoading && (
                <Box
                  component="span"
                  sx={{
                    display: 'inline-block',
                    width: 6,
                    height: 14,
                    bgcolor: theme.palette.ai.main + 'B0',
                    borderRadius: '1px',
                    ml: 0.25,
                    animation: 'pulse 1s ease-in-out infinite',
                    verticalAlign: 'text-bottom',
                  }}
                />
              )}
            </Box>
          </Box>
        );
      })}
      <div ref={messagesEndRef} />
    </Box>
  );

  const renderInput = () => (
    <Box sx={{
      px: 2,
      py: 1.5,
      borderTop: `1px solid ${theme.palette.divider}`,
    }}
    >
      {attachedFiles.length > 0 && (
        <Box sx={{
          display: 'flex',
          gap: 0.75,
          flexWrap: 'wrap',
          mb: 1,
        }}
        >
          {attachedFiles.map((f, i) => (
            <Chip
              key={String(i)}
              icon={<InsertDriveFileOutlined sx={{ fontSize: 14 }} />}
              label={f.name}
              size="small"
              variant="outlined"
              onDelete={() => setAttachedFiles(prev => prev.filter((_, j) => j !== i))}
              sx={{
                fontSize: '0.7rem',
                borderColor: theme.palette.divider,
              }}
            />
          ))}
        </Box>
      )}
      <Box sx={{
        'display': 'flex',
        'alignItems': 'center',
        'border': `1px solid ${theme.palette.divider}`,
        'borderRadius': '12px',
        'padding': '4px 4px 4px 8px',
        'transition': 'border-color 0.2s',
        '&:focus-within': { borderColor: theme.palette.ai.main },
      }}
      >
        <input
          ref={fileInputRef}
          type="file"
          multiple
          hidden
          onChange={(e) => {
            handleFileAdd(e.target.files);
            e.target.value = '';
          }}
        />
        <IconButton
          size="small"
          onClick={() => fileInputRef.current?.click()}
          sx={{
            color: theme.palette.text?.secondary,
            mr: 0.5,
          }}
        >
          <AttachFileOutlined sx={{ fontSize: 18 }} />
        </IconButton>
        <InputBase
          fullWidth
          placeholder={t('Ask a question...')}
          value={inputValue}
          onChange={e => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          onPaste={handlePaste}
          sx={{
            'fontSize': '0.8125rem',
            '& .MuiInputBase-input': { padding: '6px 0' },
          }}
          multiline
          maxRows={4}
        />
        <Tooltip title={isLoading ? t('Stop generating') : ''}>
          <IconButton
            size="small"
            onClick={isLoading ? handleStopGenerating : handleSendMessage}
            disabled={!isLoading && !inputValue.trim() && attachedFiles.length === 0}
            sx={{
              'color': (() => {
                if (isLoading) return theme.palette.error.main;
                if (inputValue.trim() || attachedFiles.length > 0) return theme.palette.ai.main;
                return theme.palette.action.disabled;
              })(),
              'bgcolor': (() => {
                if (isLoading) return theme.palette.error.main + '1A';
                if (inputValue.trim() || attachedFiles.length > 0) return theme.palette.ai.main + '1A';
                return 'transparent';
              })(),
              'borderRadius': '8px',
              'width': 32,
              'height': 32,
              'transition': 'all 0.15s ease',
              '&:hover': isLoading ? { bgcolor: theme.palette.error.main + '30' } : {},
            }}
          >
            {isLoading ? <StopCircleOutlined sx={{ fontSize: 18 }} /> : <SendOutlined sx={{ fontSize: 18 }} />}
          </IconButton>
        </Tooltip>
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
