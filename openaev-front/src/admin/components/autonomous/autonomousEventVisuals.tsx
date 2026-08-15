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

// Parsed-JSON cache for an event's `autonomous_event_data`, keyed on the event object itself. The
// timeline is append-only and each event object is an immutable snapshot (pollTimeline only ever
// appends never-before-seen ids, carrying the existing objects across by reference), so any given
// event is parsed at most once for its whole lifetime - even though the classification predicates
// below re-run over the WHOLE array on every 3s poll batch (visibleEvents / decisionEvents /
// thinkingLines each re-filter it). A WeakMap lets a dropped event (on a stream reset) be
// garbage-collected together with its cache entry. `null` is cached too (unparseable payload, or a
// non-object like a bare string/number) so a malformed payload is never re-parsed on every poll.
const parsedEventData = new WeakMap<AutonomousEvent, Record<string, unknown> | null>();

const parseEventData = (event: AutonomousEvent): Record<string, unknown> | null => {
  const cached = parsedEventData.get(event);
  if (cached !== undefined) {
    return cached;
  }
  let parsed: Record<string, unknown> | null = null;
  const raw = event.autonomous_event_data;
  if (raw) {
    try {
      const value = JSON.parse(raw) as unknown;
      if (value !== null && typeof value === 'object') {
        parsed = value as Record<string, unknown>;
      }
    } catch {
      parsed = null;
    }
  }
  parsedEventData.set(event, parsed);
  return parsed;
};

// A heartbeat is a lightweight STATUS event the XTM One orchestrator emits every ~45s WHILE a
// decision cycle is actively running (flagged {"heartbeat": true} in its data). It exists ONLY to
// keep the cockpit's "working" indicator honest and to nudge the graph poll during a long silent
// burst - it is NOT an operator-facing decision. Every surface that renders the event stream (the
// reasoning panel feed AND the overview decision timeline) filters it out so it never shows as a
// "Working" row/node, yet still reads its timestamp for freshness. Single source of truth so the
// two surfaces can never drift on what counts as a heartbeat.
export const isHeartbeatEvent = (event: AutonomousEvent | undefined): boolean => {
  if (!event || event.autonomous_event_type !== 'STATUS' || !event.autonomous_event_data) {
    return false;
  }
  // Cheap substring pre-check before the (cached) JSON.parse: most STATUS payloads never mention
  // "heartbeat" at all, so this skips the parse entirely for them.
  if (!event.autonomous_event_data.includes('"heartbeat"')) {
    return false;
  }
  return parseEventData(event)?.heartbeat === true;
};

// A live-activity NARRATION is a per-iteration "what the orchestrator is doing right now" line the
// XTM One worker streams to the timeline WHILE a decision cycle runs (flagged {"live": true} in its
// data). Unlike a genuine recorded NARRATION/DECISION, it is NOT an operator-facing decision: it
// exists ONLY to keep the cockpit's dimmed "thinking" window scrolling (it flows into thinkingLines)
// across the many iterations where the LLM narrates nothing. Every surface that renders the operator
// decision feed (the reasoning-panel rows AND the overview decision timeline/count) filters it out so
// it never shows as a decision row/node - only as streaming text in the thinking window. Single
// source of truth so the two surfaces can never drift, mirroring isHeartbeatEvent (same substring
// pre-check + shared cached parse).
export const isLiveActivityEvent = (event: AutonomousEvent | undefined): boolean => {
  if (!event || event.autonomous_event_type !== 'NARRATION' || !event.autonomous_event_data) {
    return false;
  }
  // Cheap substring pre-check before the (cached) JSON.parse: the vast majority of NARRATION
  // payloads never mention "live" at all, so this skips the parse entirely for them.
  if (!event.autonomous_event_data.includes('"live"')) {
    return false;
  }
  return parseEventData(event)?.live === true;
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

// -- Thinking window: collapsed live steps --------------------------------------------------------

// The dimmed "thinking" window streams the tail of the orchestrator's NARRATION / DECISION /
// TOOL_ACTION prose so the operator watches its train of thought. These are the only event types
// that carry live reasoning text; everything else (STATUS bookkeeping, QUESTION, DIRECTIVE) belongs
// in the decision feed, not the thought echo.
const THINKING_STREAM_EVENT_TYPES: ReadonlySet<string> = new Set(['NARRATION', 'DECISION', 'TOOL_ACTION']);

// One collapsed line of the thinking window. Consecutive events that render to the SAME caption are
// merged into a single step so a long same-caption burst (e.g. the multi-minute arsenal build where
// every iteration narrates "Searching arsenal for contracts") is one advancing line instead of a
// wall of identical glowing lines that stops visibly moving - the reported "never moves, just
// glows" symptom.
export interface ThinkingStep {
  /** Caption text, already sanitized + markdown-stripped, ready to render. */
  text: string;
  /** How many consecutive source events collapsed into this step (>= 1). */
  count: number;
  /** Epoch-ms timestamp of the FIRST event in this consecutive run (the caption-run start), so the
   *  live step can tick a monotonic per-caption elapsed clock. null when the source event carried no
   *  parseable timestamp. */
  since: number | null;
}

// Current phase of the thinking window (label + colour + whether the orchestrator is actively
// working). Shared with the panel and the ThinkingBubble component so the phase shape has one
// definition across the caption logic and the renderer.
export interface ThinkingPhase {
  key: string;
  label: string;
  color: string;
  // Whether the orchestrator is actively working (mid-cycle) vs. idle/parked (awaiting a
  // human-timescale event or the operator's answer). Only an ACTIVE phase pulses and streams the
  // live thought echo; an idle phase settles into a calm, static waiting indicator so a parked run
  // does not look like it is still computing.
  active: boolean;
}

// Collapse the reasoning stream into thinking-window steps, merging CONSECUTIVE identical captions
// into one step that carries a repeat count and the run-start timestamp. Only adjacent duplicates
// are merged (a caption that recurs after a different one starts a fresh step), so genuine progress
// still reads as a moving list while a same-caption burst becomes a single line the renderer can
// advance with a ticking timer. The last returned step is the live one; earlier steps are finalized
// history. Keeps at most `limit` steps (the most recent), matching the window's visible capacity.
export const collapseThinkingSteps = (
  events: AutonomousEvent[],
  limit = 8,
): ThinkingStep[] => {
  const steps: ThinkingStep[] = [];
  for (const event of events) {
    if (!THINKING_STREAM_EVENT_TYPES.has(event.autonomous_event_type)) {
      continue;
    }
    const text = stripMarkdown(sanitizeEventText(event.autonomous_event_content ?? event.autonomous_event_title));
    if (!text) {
      continue;
    }
    const at = event.autonomous_event_created_at ? new Date(event.autonomous_event_created_at).getTime() : Number.NaN;
    const since = Number.isFinite(at) ? at : null;
    const last = steps[steps.length - 1];
    if (last && last.text === text) {
      // Same caption as the current live step: fold it in and KEEP the first timestamp so the
      // per-caption clock measures the whole burst (never resets while the caption holds).
      last.count += 1;
    } else {
      steps.push({
        text,
        count: 1,
        since,
      });
    }
  }
  return steps.slice(-limit);
};

// Phase keys whose label is a GENERIC placeholder ("Getting to work", "Analyzing the results",
// "Thinking through the next move") rather than a specific, human-meaningful state. During a long
// live burst these are the labels that freeze on-screen while the orchestrator is demonstrably
// narrating changing captions, so for these phases the bold label should yield to the live caption.
// The specific phases (Consulting <agent>, Capturing proof, Deciding the next move, parked/waiting,
// stalled...) keep their own label because it conveys more than the raw narration line.
export const GENERIC_WORKING_PHASE_KEYS: ReadonlySet<string> = new Set(['engaging', 'analyzing', 'thinking']);

// Resolve the bold thinking-window label: for a generic working phase with a live caption available,
// show what the orchestrator is narrating RIGHT NOW so the label tracks the live stream (instead of
// freezing on a generic phrase for the whole burst); otherwise keep the phase's own label.
export const resolveLiveLabel = (
  phaseKey: string,
  phaseActive: boolean,
  fallbackLabel: string,
  liveCaption: string | null | undefined,
): string => (
  phaseActive && !!liveCaption && GENERIC_WORKING_PHASE_KEYS.has(phaseKey)
    ? liveCaption
    : fallbackLabel
);

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
