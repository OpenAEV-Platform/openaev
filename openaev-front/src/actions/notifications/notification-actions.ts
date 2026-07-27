import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { NotificationBulkProcessingInput, SearchPaginationInput } from '../../utils/api-types';

const NOTIFICATION_URI = '/api/notifications';

export const searchMyNotifications = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${NOTIFICATION_URI}/me/search`, searchPaginationInput);
};

export const getUnreadNotificationsCount = () => {
  return simpleCall(`${NOTIFICATION_URI}/me/unread-count`);
};

export const markNotificationRead = (notificationId: string, read: boolean) => {
  return simplePutCall(`${NOTIFICATION_URI}/${notificationId}/read?read=${read}`, {});
};

export const markAllNotificationsRead = () => {
  return simplePutCall(`${NOTIFICATION_URI}/me/read-all`, {});
};

export const deleteNotification = (notificationId: string) => {
  return simpleDelCall(`${NOTIFICATION_URI}/${notificationId}`);
};

export const bulkDeleteNotifications = (input: NotificationBulkProcessingInput) => {
  return simplePostCall(`${NOTIFICATION_URI}/me/bulk-delete`, input);
};

export const bulkMarkNotificationsRead = (input: NotificationBulkProcessingInput, read: boolean) => {
  return simplePutCall(`${NOTIFICATION_URI}/me/bulk-read?read=${read}`, input);
};
