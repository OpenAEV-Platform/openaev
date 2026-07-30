import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { NotificationTriggerInput, SearchPaginationInput } from '../../utils/api-types';

const NOTIFICATION_TRIGGER_URI = '/api/notification-triggers';

export const searchNotificationTriggers = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${NOTIFICATION_TRIGGER_URI}/search`, searchPaginationInput);
};

export const fetchNotificationTrigger = (triggerId: string) => {
  return simpleCall(`${NOTIFICATION_TRIGGER_URI}/${triggerId}`);
};

export const createNotificationTrigger = (data: NotificationTriggerInput) => {
  return simplePostCall(NOTIFICATION_TRIGGER_URI, data);
};

export const updateNotificationTrigger = (triggerId: string, data: NotificationTriggerInput) => {
  return simplePutCall(`${NOTIFICATION_TRIGGER_URI}/${triggerId}`, data);
};

export const deleteNotificationTrigger = (triggerId: string) => {
  return simpleDelCall(`${NOTIFICATION_TRIGGER_URI}/${triggerId}`);
};
