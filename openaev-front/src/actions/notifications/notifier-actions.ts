import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../utils/Action';
import type { NotifierInput, SearchPaginationInput } from '../../utils/api-types';

const NOTIFIER_URI = '/api/notifiers';

export const fetchNotifiers = () => {
  return simpleCall(NOTIFIER_URI);
};

export const searchNotifiers = (searchPaginationInput: SearchPaginationInput) => {
  return simplePostCall(`${NOTIFIER_URI}/search`, searchPaginationInput);
};

export const createNotifier = (data: NotifierInput) => {
  return simplePostCall(NOTIFIER_URI, data);
};

export const updateNotifier = (notifierId: string, data: NotifierInput) => {
  return simplePutCall(`${NOTIFIER_URI}/${notifierId}`, data);
};

export const deleteNotifier = (notifierId: string) => {
  return simpleDelCall(`${NOTIFIER_URI}/${notifierId}`);
};

export const testNotifier = (notifierId: string) => {
  return simplePostCall(`${NOTIFIER_URI}/${notifierId}/test`, {});
};
