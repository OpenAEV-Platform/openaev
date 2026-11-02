import { simpleCall, simplePutCall } from '../../utils/Action';
import type { NotificationPreferenceInput } from '../../utils/api-types';

// Global to the user (not per tenant), like the language or the theme.
const NOTIFICATION_PREFERENCE_URI = '/api/notification-preferences';

export const fetchMyNotificationPreferences = () => {
  return simpleCall(`${NOTIFICATION_PREFERENCE_URI}/me`);
};

export const updateMyNotificationPreferences = (input: NotificationPreferenceInput[]) => {
  return simplePutCall(`${NOTIFICATION_PREFERENCE_URI}/me`, input);
};
