import { deepPurple, green, indigo, orange, red } from '@mui/material/colors';
import { BellAlertOutline, BellCogOutline, BellOutline, BellPlusOutline, BellRemoveOutline, FileTableBoxMultipleOutline } from 'mdi-material-ui';
import { type ReactNode } from 'react';

import {
  ASSET_BASE_URL,
  ASSET_GROUP_BASE_URL,
  ATOMIC_BASE_URL,
  FINDING_BASE_URL,
  PERSON_BASE_URL,
  SCENARIO_BASE_URL,
  SECURITY_PLATFORM_BASE_URL,
  SIMULATION_BASE_URL,
  TEAM_BASE_URL,
} from '../../../../constants/BaseUrls';
import { type NotificationOutput } from '../../../../utils/api-types';

/** A single matched event inside a notification content group. */
export interface NotificationEvent {
  operation?: string;
  message?: string;
  resource_type?: string;
  resource_id?: string;
}

/** One notification content group: live = single group/event, digest = one group per trigger. */
export interface NotificationContentGroup {
  title?: string;
  events?: NotificationEvent[];
}

export const contentGroupsOf = (notification: NotificationOutput): NotificationContentGroup[] =>
  (notification.notification_content ?? []) as NotificationContentGroup[];

export const eventsOf = (notification: NotificationOutput): NotificationEvent[] =>
  contentGroupsOf(notification).flatMap(group => group.events ?? []);

/**
 * The operation displayed for a notification: digests aggregate several events
 * and show as MULTIPLE, live notifications show their single event operation
 * (mirrors OpenCTI's notification list).
 */
export const getFirstOperation = (notification: NotificationOutput): string => {
  if (notification.notification_type === 'DIGEST') {
    return 'MULTIPLE';
  }
  return eventsOf(notification).at(0)?.operation ?? 'NONE';
};

/** Operation accent colors (mirrors OpenCTI's notification palette). */
export const OPERATION_COLORS: Record<string, string> = {
  NONE: green[500],
  CREATE: green[500],
  UPDATE: deepPurple[500],
  DELETE: red[500],
  SCORE_DEGRADATION: orange[500],
  MULTIPLE: indigo[500],
};

export const operationColor = (operation: string): string => OPERATION_COLORS[operation] ?? indigo[500];

/** i18n keys of the operation labels shown in the Operation chip. */
export const OPERATION_LABELS: Record<string, string> = {
  NONE: 'Unknown',
  CREATE: 'Creation',
  UPDATE: 'Modification',
  DELETE: 'Deletion',
  SCORE_DEGRADATION: 'Score degradation',
  MULTIPLE: 'Multiple',
};

export const operationLabel = (operation: string): string => OPERATION_LABELS[operation] ?? 'Unknown';

/** Bell icon per operation (mirrors OpenCTI's iconSelector). */
export const operationIcon = (operation: string): ReactNode => {
  const color = operationColor(operation);
  switch (operation) {
    case 'CREATE':
      return <BellPlusOutline style={{ color }} />;
    case 'UPDATE':
      return <BellCogOutline style={{ color }} />;
    case 'DELETE':
      return <BellRemoveOutline style={{ color }} />;
    case 'SCORE_DEGRADATION':
      return <BellAlertOutline style={{ color }} />;
    case 'MULTIPLE':
      return <FileTableBoxMultipleOutline style={{ color }} />;
    default:
      return <BellOutline style={{ color }} />;
  }
};

// Resource types with a dedicated detail page. Types without a standalone
// detail route (payload, vulnerability, document, challenge) are deliberately
// absent: their notifications are not navigable.
const RESOURCE_TYPE_URLS: Record<string, (id: string) => string> = {
  SCENARIO: id => `${SCENARIO_BASE_URL}/${id}`,
  SIMULATION: id => `${SIMULATION_BASE_URL}/${id}`,
  INJECT: id => `${ATOMIC_BASE_URL}/${id}`,
  FINDING: id => `${FINDING_BASE_URL}/${id}`,
  ASSET: id => `${ASSET_BASE_URL}/${id}`,
  ASSET_GROUP: id => `${ASSET_GROUP_BASE_URL}/${id}`,
  TEAM: id => `${TEAM_BASE_URL}/${id}`,
  PLAYER: id => `${PERSON_BASE_URL}/${id}`,
  SECURITY_PLATFORM: id => `${SECURITY_PLATFORM_BASE_URL}/${id}`,
};

/**
 * URL of the entity that triggered an event, or undefined when not navigable
 * (deleted entity, unknown resource type, or type without a detail page).
 */
export const eventUrl = (event?: NotificationEvent): string | undefined => {
  if (!event?.resource_id || !event.resource_type || event.operation === 'DELETE') {
    return undefined;
  }
  return RESOURCE_TYPE_URLS[event.resource_type]?.(event.resource_id);
};
