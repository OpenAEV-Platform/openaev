import {
  BlockOutlined,
  CancelOutlined,
  CheckCircleOutlined,
  DoneAllOutlined,
  ErrorOutlineOutlined,
  HelpOutlineOutlined,
  HourglassEmptyOutlined,
  InfoOutlined,
  PauseCircleOutlined,
  PlayCircleOutlineOutlined,
  RemoveCircleOutlineOutlined,
  ScheduleOutlined,
  TimerOffOutlined,
  WarningAmberOutlined,
} from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { type ComponentType } from 'react';

// Single source of truth mapping every inject / execution / expectation status
// to a meaningful icon. Keys are lowercased to match getStatusColor. Used by the
// status chip and the trace log rows so a given status shows the same icon
// everywhere (e.g. TIMEOUT is a timer-off, not a question mark).
const STATUS_ICON: Record<string, ComponentType<SvgIconProps>> = {
  // -- Success --
  'executed': CheckCircleOutlined,
  'success': CheckCircleOutlined,
  'successful': CheckCircleOutlined,
  'ok': CheckCircleOutlined,
  'access_denied': CheckCircleOutlined, // the control blocked the attack - a good outcome
  'prevented': CheckCircleOutlined,
  'detected': CheckCircleOutlined,
  'not vulnerable': CheckCircleOutlined,
  'running': PlayCircleOutlineOutlined,
  'on-going': PlayCircleOutlineOutlined,
  'executing': PlayCircleOutlineOutlined,

  // -- Warning / partial --
  'warning': WarningAmberOutlined,
  'executed_with_cleanup_failure': WarningAmberOutlined,
  'partial': WarningAmberOutlined,
  'partially prevented': WarningAmberOutlined,
  'partially detected': WarningAmberOutlined,
  'paused': WarningAmberOutlined,

  // -- Error / failed --
  'error': ErrorOutlineOutlined,
  'command_not_found': ErrorOutlineOutlined,
  'command_cannot_be_executed': ErrorOutlineOutlined,
  'prerequisite_failed': ErrorOutlineOutlined,
  'invalid_usage': ErrorOutlineOutlined,
  'failed': ErrorOutlineOutlined,
  'not prevented': ErrorOutlineOutlined,
  'not detected': ErrorOutlineOutlined,
  'undetected': ErrorOutlineOutlined,
  'unprevented': ErrorOutlineOutlined,
  'vulnerable': ErrorOutlineOutlined,
  'asset_inactive': ErrorOutlineOutlined,
  // @deprecated - rerouted to error in backend
  'maybe_prevented': ErrorOutlineOutlined,
  'maybe_partial_prevented': ErrorOutlineOutlined,

  // -- Distinct operational statuses --
  'timeout': TimerOffOutlined,
  'interrupted': BlockOutlined,
  'queuing': HourglassEmptyOutlined,
  'pending': ScheduleOutlined,
  'scheduled': ScheduleOutlined,
  'info': InfoOutlined,

  // -- Muted / not applicable --
  'asset_agentless': PauseCircleOutlined,
  'agent_inactive': PauseCircleOutlined,
  'agent_overloaded': PauseCircleOutlined,
  'draft': PauseCircleOutlined,
  'canceled': CancelOutlined,
  'finished': DoneAllOutlined,
  'not_planned': RemoveCircleOutlineOutlined,
};

// Returns the icon component for a status, falling back to a help icon only for
// genuinely unknown values (e.g. the synthesized "Unknown").
export const getStatusIconComponent = (status: string | undefined): ComponentType<SvgIconProps> => {
  return STATUS_ICON[(status ?? '').toLowerCase()] ?? HelpOutlineOutlined;
};

export default getStatusIconComponent;
