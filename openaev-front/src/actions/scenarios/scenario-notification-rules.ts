import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { CreateNotificationRuleInput, NotificationRuleOutput, UpdateNotificationRuleInput } from '../../utils/api-types';

const NOTIFICATION_RULE_URI = '/api/notification-rules';

export const createNotificationRule = (data: CreateNotificationRuleInput) => {
  return simplePostCall<NotificationRuleOutput>(NOTIFICATION_RULE_URI, data, undefined, true, true);
};

export const updateNotificationRule = (notificationRuleId: string, data: UpdateNotificationRuleInput) => {
  const uri = `${NOTIFICATION_RULE_URI}/${notificationRuleId}`;
  return simplePutCall<NotificationRuleOutput>(uri, data);
};

export const deleteNotificationRule = (notificationRuleId: string) => {
  const uri = `${NOTIFICATION_RULE_URI}/${notificationRuleId}`;
  return simpleDelCall(uri);
};

export const findNotificationRuleByResource = (resourceId: string) => {
  const uri = `${NOTIFICATION_RULE_URI}/resource/${resourceId}`;
  return simpleCall<NotificationRuleOutput[]>(uri);
};
