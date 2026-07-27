import { DnsOutlined, GroupsOutlined, PersonOutlined, PlayCircleOutlineOutlined, PublicOutlined, RouteOutlined } from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { SelectGroup, Target } from 'mdi-material-ui';
import { type ComponentType } from 'react';

import { type Reporting } from '../../../utils/api-types';

export type ReportingContextType = Reporting['reporting_context_type'];

// Same icons as the corresponding left-menu / entity surfaces, so a report's
// subject is recognizable at a glance.
export const REPORTING_CONTEXT_ICONS: Record<ReportingContextType, ComponentType<SvgIconProps>> = {
  PLATFORM: PublicOutlined,
  SIMULATION: PlayCircleOutlineOutlined,
  SCENARIO: RouteOutlined,
  ATOMIC_TESTING: Target,
  ENDPOINT: DnsOutlined,
  ASSET_GROUP: SelectGroup,
  PLAYER: PersonOutlined,
  TEAM: GroupsOutlined,
};

// i18n keys - translated through useFormatter's t()
export const REPORTING_CONTEXT_LABELS: Record<ReportingContextType, string> = {
  PLATFORM: 'Platform',
  SIMULATION: 'Simulation',
  SCENARIO: 'Scenario',
  ATOMIC_TESTING: 'Atomic testing',
  ENDPOINT: 'Endpoint',
  ASSET_GROUP: 'Asset group',
  PLAYER: 'Player',
  TEAM: 'Team',
};

// Latest generation of a report (generations are returned most recent first,
// but sort defensively as the API contract only guarantees the collection).
export const latestGeneration = (reporting: Reporting) => {
  const generations = reporting.reporting_generations ?? [];
  if (generations.length === 0) return undefined;
  return [...generations].sort((a, b) =>
    (b.reporting_generation_created_at ?? '').localeCompare(a.reporting_generation_created_at ?? ''),
  )[0];
};
