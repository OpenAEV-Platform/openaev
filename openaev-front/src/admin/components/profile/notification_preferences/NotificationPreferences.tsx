import { Paper, Switch, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';

import {
  fetchMyNotificationPreferences,
  updateMyNotificationPreferences,
} from '../../../../actions/notifications/notification-preference-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { type NotificationPreferenceOutput } from '../../../../utils/api-types';
import NotificationCenterTabs from '../NotificationCenterTabs';

type PreferenceType = NotificationPreferenceOutput['notification_preference_type'];

const TYPES: {
  type: PreferenceType;
  label: string;
  description: string;
}[] = [
  {
    type: 'PAYLOAD_APPROVAL_REQUESTS',
    label: 'Payloads to approve',
    description: 'A payload, or a new version of a payload, is waiting for your approval (if your role holds Approve content).',
  },
  {
    type: 'PAYLOAD_APPROVAL_OUTCOMES',
    label: 'Outcome of my payloads',
    description: 'A payload or version you submitted was approved, rejected or replaced by a newer version.',
  },
];

/**
 * Notification center, Preferences tab: per notification type, whether it shows in the notification
 * center (in-app) and whether it is also sent by email. Global to the user, not per tenant.
 */
const NotificationPreferences = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [preferences, setPreferences] = useState<NotificationPreferenceOutput[]>([]);

  useEffect(() => {
    fetchMyNotificationPreferences().then(response => setPreferences(response.data as NotificationPreferenceOutput[]));
  }, []);

  const preferenceOf = (type: PreferenceType) => preferences.find(p => p.notification_preference_type === type);

  const update = (type: PreferenceType, change: Partial<Pick<NotificationPreferenceOutput, 'notification_preference_in_app' | 'notification_preference_email'>>) => {
    const current = preferenceOf(type);
    updateMyNotificationPreferences([{
      notification_preference_type: type,
      notification_preference_in_app: change.notification_preference_in_app ?? current?.notification_preference_in_app ?? true,
      notification_preference_email: change.notification_preference_email ?? current?.notification_preference_email ?? false,
    }]).then(response => setPreferences(response.data as NotificationPreferenceOutput[]));
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Notification center'),
          current: true,
        }]}
      />
      <NotificationCenterTabs current="preferences" />
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
      >
        {TYPES.map(({ type, label, description }) => {
          const preference = preferenceOf(type);
          return (
            <Paper key={type} padding={16}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: theme.spacing(2),
              }}
              >
                <div>
                  <Text variant="content-base" style={{ fontWeight: 600 }}>{t(label)}</Text>
                  <Text variant="content-caption" className="text-default-secondary" style={{ display: 'block' }}>
                    {t(description)}
                  </Text>
                </div>
                <div style={{
                  display: 'flex',
                  gap: theme.spacing(3),
                  flexShrink: 0,
                }}
                >
                  <Switch
                    label={t('In-app')}
                    disabled={!preference}
                    checked={preference?.notification_preference_in_app ?? true}
                    onCheckedChange={checked => update(type, { notification_preference_in_app: checked === true })}
                  />
                  <Switch
                    label={t('Email')}
                    disabled={!preference}
                    checked={preference?.notification_preference_email ?? false}
                    onCheckedChange={checked => update(type, { notification_preference_email: checked === true })}
                  />
                </div>
              </div>
            </Paper>
          );
        })}
      </div>
    </>
  );
};

export default NotificationPreferences;
