import { Paper, Switch, Text } from '@filigran/design-system';
// fds:keep-mui Divider has no library equivalent yet
import { Divider } from '@mui/material';
import { useId } from 'react';

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

interface SettingRowProps {
  label: string;
  checked: boolean;
  disabled: boolean;
  /** The main switch: its label is emphasised, the ones of the types are secondary. */
  primary?: boolean;
  onCheckedChange: (checked: boolean) => void;
}

/** A label on the left, its switch on the right. The label is the switch's accessible name and toggles it. */
const SettingRow = ({ label, checked, disabled, primary = false, onCheckedChange }: SettingRowProps) => {
  const id = useId();

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: 16,
    }}
    >
      <Text
        as="label"
        htmlFor={id}
        variant={primary ? 'content-base-bold' : 'content-compact'}
        className={primary ? undefined : 'text-default-secondary'}
      >
        {label}
      </Text>
      <Switch id={id} checked={checked} disabled={disabled} onCheckedChange={onCheckedChange} />
    </div>
  );
};

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
      gap: 8,
      width: '50%',
    }}
    >
      <Text variant="content-caption" as="h2" className="text-default-secondary" style={{ margin: 0 }}>{t('XTM Hub News feed settings')}</Text>
      <Paper padding={24}>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 20,
        }}
        >
          <SettingRow
            primary
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
            padding: '0 20px',
          }}
          >
            {NEWS_FEED_TYPES.map(type => (
              <SettingRow
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
