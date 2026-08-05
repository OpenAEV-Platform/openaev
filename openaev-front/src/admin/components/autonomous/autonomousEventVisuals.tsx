/* eslint-disable react-refresh/only-export-components -- shared visual helpers (icons/accents/labels), not a component module */
import {
  AccountTreeOutlined,
  AutoAwesome,
  BoltOutlined,
  CancelOutlined,
  CheckCircleOutline,
  ExtensionOutlined,
  FiberNewOutlined,
  HelpOutline,
  InfoOutlined,
  PauseCircleOutline,
  PlayCircleOutline,
  SendOutlined,
  SmartToyOutlined,
  WarningAmberOutlined,
} from '@mui/icons-material';
import { Box } from '@mui/material';
import type { Theme } from '@mui/material/styles';
import { type ReactNode } from 'react';

import { type AutonomousEvent, type AutonomousEventType } from '../../../actions/autonomous/autonomous-types';
import MarkdownDisplay from '../../../components/MarkdownDisplay';

// Shared visual language for autonomous timeline events, so every surface that renders the run's
// event stream (the always-open reasoning panel and the overview decision timeline) uses the exact
// same icon + accent per event type - no drift between the two.

// STATUS events all share one type but represent very different lifecycle moments (created, started,
// paused, canceled, completed...). Classify by the (English, backend-authored) title so each step
// reads with its own icon + color instead of a wall of identical sparkles.
export type StatusFlavor = 'created' | 'running' | 'paused' | 'ended' | 'completed' | 'default';

export const statusFlavor = (title?: string | null): StatusFlavor => {
  const value = (title ?? '').toLowerCase();
  if (value.includes('creat')) return 'created';
  if (value.includes('start') || value.includes('resum') || value.includes('running')) return 'running';
  if (value.includes('paus')) return 'paused';
  if (value.includes('cancel') || value.includes('fail') || value.includes('error') || value.includes('stop')) return 'ended';
  if (value.includes('complet') || value.includes('finish') || value.includes('done')) return 'completed';
  return 'default';
};

// Per-event accent so the stream reads at a glance: reasoning is AI-purple, tool actions blue,
// proofs green, gaps/questions amber, and each STATUS step gets a lifecycle-appropriate tone.
export const eventAccent = (event: AutonomousEvent, theme: Theme): string => {
  switch (event.autonomous_event_type) {
    case 'NARRATION':
      return theme.palette.ai?.main ?? theme.palette.primary.main;
    case 'DECISION':
      return theme.palette.primary.main;
    case 'TOOL_ACTION':
    case 'HANDOVER':
      return theme.palette.info.main;
    case 'AGENT_DELEGATION':
      return theme.palette.ai?.main ?? theme.palette.secondary.main;
    case 'GAP':
    case 'QUESTION':
      return theme.palette.warning.main;
    case 'PROOF':
      return theme.palette.success.main;
    case 'DIRECTIVE':
      return theme.palette.secondary.main;
    case 'STATUS':
      switch (statusFlavor(event.autonomous_event_title)) {
        case 'created':
        case 'running':
          return theme.palette.info.main;
        case 'paused':
          return theme.palette.warning.main;
        case 'ended':
          return theme.palette.error.main;
        case 'completed':
          return theme.palette.success.main;
        default:
          return theme.palette.text.disabled;
      }
    default:
      return theme.palette.text.disabled;
  }
};

// Human-readable label for an event type. The raw enum values are SCREAMING_SNAKE_CASE, so passing
// them straight to t() renders the untranslated fallback with the underscore intact (e.g.
// "Tool_action"). Map to clean English keys the translation layer can localize and that read
// correctly even when a locale has no entry yet.
export const eventTypeLabel = (type?: AutonomousEventType | string | null): string => {
  switch (type) {
    case 'NARRATION':
      return 'Narration';
    case 'DECISION':
      return 'Decision';
    case 'TOOL_ACTION':
      return 'Action';
    case 'HANDOVER':
      return 'Handover';
    case 'AGENT_DELEGATION':
      return 'Agent delegation';
    case 'GAP':
      return 'Capability gap';
    case 'STATUS':
      return 'Status';
    case 'DIRECTIVE':
      return 'Directive';
    case 'QUESTION':
      return 'Question';
    case 'PROOF':
      return 'Proof';
    default:
      return 'Event';
  }
};

// Defensive display-time cleanup: the orchestrator (an LLM) occasionally leaks its own tool-call
// framing into an event's title/content - operator prose followed by literal </content>, <invoke ...>,
// <parameter ...> markup of the next calls. XTM One now strips this at the source, but runs recorded
// before that fix still carry it, so cut every rendered string at the first such marker as a backstop
// so raw XML never shows in the reasoning panel or the overview timeline.
const TOOL_MARKUP_CUTOFF = /<\/?\s*(?:antml:)?(?:invoke|parameter|function_calls|function_result|function|content)\b/i;

export const sanitizeEventText = (text?: string | null): string => {
  if (!text) {
    return '';
  }
  const match = text.match(TOOL_MARKUP_CUTOFF);
  const cleaned = match && match.index !== undefined ? text.slice(0, match.index) : text;
  return cleaned.trim();
};

// Flatten Markdown to plain text for compact, line-clamped previews (e.g. the gap/proof teaser
// cards) where the full rich body is one click away in a dialog. Rendering block Markdown (tables,
// code fences) inside a 3-line -webkit-line-clamp does not clamp and would leak raw '**' / '|'
// syntax, so strip the syntax to keep the teaser clean while the dialog keeps the rich formatting.
export const stripMarkdown = (text?: string | null): string => {
  if (!text) {
    return '';
  }
  return text
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^\s{0,3}#{1,6}\s+/gm, '')
    .replace(/^\s{0,3}>\s?/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+\.\s+/gm, '')
    .replace(/^\s*[-*_]{3,}\s*$/gm, '')
    .replace(/\|/g, ' ')
    .replace(/(\*\*|__)(.*?)\1/g, '$2')
    .replace(/(\*|_)(.*?)\1/g, '$2')
    .replace(/\s+/g, ' ')
    .trim();
};

// The orchestrator authors its narration / decisions / proofs / gaps in GitHub-flavored Markdown
// (bold, lists, tables, inline code, fenced code for commands). Render it through the platform's
// standard MarkdownDisplay - the same renderer the rest of the app uses - so the reasoning reads like
// the XTM One chat instead of a raw text wall. Styling is scoped to stay compact inside the reasoning
// panel / timeline card (tight margins, small code blocks) rather than page-sized markdown.
export const EventMarkdown = ({
  content,
  color,
  fontSize = '0.75rem',
}: {
  content: string;
  color?: string;
  fontSize?: number | string;
}): ReactNode => (
  <Box
    sx={{
      'color': color ?? 'text.secondary',
      fontSize,
      'lineHeight': 1.5,
      'wordBreak': 'break-word',
      '& > *:first-of-type': { marginTop: 0 },
      '& > *:last-of-type': { marginBottom: 0 },
      '& p': { margin: '3px 0' },
      '& ul, & ol': {
        margin: '3px 0',
        paddingLeft: '1.25rem',
      },
      '& li': { margin: '1px 0' },
      '& li > p': { margin: 0 },
      '& h1, & h2, & h3, & h4, & h5, & h6': {
        margin: '6px 0 2px',
        fontSize: '0.8125rem',
        fontWeight: 700,
        lineHeight: 1.3,
      },
      '& strong': { fontWeight: 700 },
      '& a': { color: 'primary.main' },
      '& code': {
        fontFamily: 'monospace',
        fontSize: '0.85em',
        padding: '1px 4px',
        borderRadius: 0.5,
        backgroundColor: 'action.hover',
        wordBreak: 'break-all',
      },
      '& pre': {
        margin: '4px 0',
        padding: 1,
        borderRadius: 1,
        overflowX: 'auto',
        backgroundColor: 'action.hover',
      },
      '& pre code': {
        padding: 0,
        backgroundColor: 'transparent',
        wordBreak: 'normal',
      },
      '& blockquote': {
        margin: '4px 0',
        paddingLeft: 1,
        borderLeft: '2px solid',
        borderColor: 'divider',
        color: 'text.secondary',
      },
      '& table': {
        borderCollapse: 'collapse',
        margin: '4px 0',
        fontSize: '0.95em',
        display: 'block',
        overflowX: 'auto',
      },
      '& th, & td': {
        border: '1px solid',
        borderColor: 'divider',
        padding: '3px 6px',
        textAlign: 'left',
      },
      '& th': { fontWeight: 700 },
      '& hr': {
        margin: '6px 0',
        border: 0,
        borderTop: '1px solid',
        borderColor: 'divider',
      },
    }}
  >
    <MarkdownDisplay content={content} remarkGfmPlugin />
  </Box>
);

export const eventIcon = (event: AutonomousEvent): ReactNode => {
  switch (event.autonomous_event_type) {
    case 'DECISION':
      return <BoltOutlined fontSize="small" />;
    case 'TOOL_ACTION':
      return <ExtensionOutlined fontSize="small" />;
    case 'GAP':
      return <WarningAmberOutlined fontSize="small" />;
    case 'QUESTION':
      return <HelpOutline fontSize="small" />;
    case 'PROOF':
      return <CheckCircleOutline fontSize="small" />;
    case 'DIRECTIVE':
      return <SendOutlined fontSize="small" />;
    case 'HANDOVER':
      return <AccountTreeOutlined fontSize="small" />;
    case 'AGENT_DELEGATION':
      return <SmartToyOutlined fontSize="small" />;
    case 'STATUS':
      switch (statusFlavor(event.autonomous_event_title)) {
        case 'created':
          return <FiberNewOutlined fontSize="small" />;
        case 'running':
          return <PlayCircleOutline fontSize="small" />;
        case 'paused':
          return <PauseCircleOutline fontSize="small" />;
        case 'ended':
          return <CancelOutlined fontSize="small" />;
        case 'completed':
          return <CheckCircleOutline fontSize="small" />;
        default:
          return <InfoOutlined fontSize="small" />;
      }
    default:
      return <AutoAwesome fontSize="small" />;
  }
};
