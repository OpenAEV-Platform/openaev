import type { NotificationOutput } from '../../utils/api-types';

export interface NotificationHelper { getNotifications: () => NotificationOutput[] }
