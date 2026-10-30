import { Paper, Switch, Text } from '@filigran/design-system';
// fds:keep-mui Divider has no library equivalent yet
import { Divider } from '@mui/material';

import { useFormatter } from '../../../../components/i18n';
import SimpleError from '../../../../components/SimpleError';
import type { NewsFeedSettingsOutput } from '../../../../utils/api-types';
import { NEWS_FEED_TYPES, type NewsFeedType } from './useNewsFeedSettings';

interface Props {
  settings: NewsFeedSettingsOutput | null;
  failed: boolean;
  saving: boolean;
  onChange: (subscribedTypes: NewsFeedType[]) => void;
}

/** Subscriptions of the user: one switch per news feed type, and one that turns them all on or off. */
const NewsFeedSettings = ({ settings, failed, saving, onChange }: Props) => {
  const { t } = useFormatter();

  if (failed) {
    return <SimpleError />;
  }
  if (!settings) {
    return null;
  }

  const subscribedTypes = settings.news_feed_settings_subscribed_types;
  // Changing the preferences is only possible when XTM Hub delivers the items.
  const disabled = saving || !settings.news_feed_settings_hub_registered;

  const typeLabel = (type: NewsFeedType) => (type === 'RESOURCE_SCENARIO' ? t('Scenario') : type);

  const toggleType = (type: NewsFeedType, checked: boolean) => {
    onChange(checked ? [...subscribedTypes, type] : subscribedTypes.filter(subscribed => subscribed !== type));
  };

  return (
    <section style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      <Text variant="title-sm" as="h2" style={{ margin: 0 }}>{t('XTM Hub News feed settings')}</Text>
      <Paper padding={16}>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          <Switch
            label={t('Enable News Feed notifications')}
            checked={subscribedTypes.length > 0}
            disabled={disabled}
            onCheckedChange={checked => onChange(checked ? NEWS_FEED_TYPES : [])}
          />
          <Divider />
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
            paddingLeft: 16,
          }}
          >
            {NEWS_FEED_TYPES.map(type => (
              <Switch
                key={type}
                label={typeLabel(type)}
                checked={subscribedTypes.includes(type)}
                disabled={disabled}
                onCheckedChange={checked => toggleType(type, checked)}
              />
            ))}
          </div>
        </div>
      </Paper>
    </section>
  );
};

export default NewsFeedSettings;
