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
  WarningAmberOutlined,
} from '@mui/icons-material';
import type { Theme } from '@mui/material/styles';
import { type ReactNode } from 'react';

import { type AutonomousEvent, type AutonomousEventType } from '../../../actions/autonomous/autonomous-types';

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
