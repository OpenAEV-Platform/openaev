import { Paper, Switch, Text } from '@filigran/design-system';
// fds:keep-mui Divider has no library equivalent yet
import { Divider } from '@mui/material';
import { useId } from 'react';

import { useFormatter } from '../../../../components/i18n';
import SimpleError from '../../../../components/SimpleError';
import type { NewsFeedSettingsOutput } from '../../../../utils/api-types';
import { NEWS_FEED_TYPES, type NewsFeedType } from './useNewsFeedSettings';

// The scenarios are still delivered and subscribed, the user just has no switch for them.
const HIDDEN_TYPES: NewsFeedType[] = ['RESOURCE_SCENARIO'];

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

/**
 * Subscriptions of the user: one switch per news feed type XTM Hub provisions, as on OpenCTI, and one that turns
 * every type on or off.
 */
const NewsFeedSettings = ({ settings, failed, saving, onChange }: Props) => {
  const { t } = useFormatter();

  if (failed) {
    return <SimpleError />;
  }
  if (!settings) {
    return null;
  }

  const subscribedTypes = settings.news_feed_settings_subscribed_types;
  // Until XTM Hub told which types it provisions (no collector run since the upgrade), every known type is listed.
  const listedTypes = settings.news_feed_settings_available_types.length > 0 ? settings.news_feed_settings_available_types : NEWS_FEED_TYPES;
  // On when a listed type is: a subscription to a type that is not listed shows nowhere.
  const isAnyListedTypeSubscribed = listedTypes.some(type => subscribedTypes.includes(type));
  // Changing the preferences is only possible when XTM Hub delivers the items.
  const disabled = saving || !settings.news_feed_settings_hub_registered;

  const switchableTypes = listedTypes.filter(type => !HIDDEN_TYPES.includes(type));

  const typeLabels: Record<NewsFeedType, string> = {
    RESOURCE_SCENARIO: t('Scenario'),
    BLOG_POST: t('Blogpost'),
    GITHUB_RELEASE: t('GitHub release'),
  };

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
            checked={isAnyListedTypeSubscribed}
            disabled={disabled}
            onCheckedChange={checked => onChange(checked ? NEWS_FEED_TYPES : [])}
          />
          {switchableTypes.length > 0 && (
            <>
              <Divider />
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 16,
                padding: '0 20px',
              }}
              >
                {switchableTypes.map(type => (
                  <SettingRow
                    key={type}
                    label={typeLabels[type]}
                    checked={subscribedTypes.includes(type)}
                    disabled={disabled}
                    onCheckedChange={checked => toggleType(type, checked)}
                  />
                ))}
              </div>
            </>
          )}
        </div>
      </Paper>
    </section>
  );
};

export default NewsFeedSettings;
