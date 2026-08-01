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

import { type AutonomousEvent } from '../../../actions/autonomous/autonomous-types';

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
