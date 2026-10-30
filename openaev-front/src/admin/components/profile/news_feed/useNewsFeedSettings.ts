import { useCallback, useEffect, useState } from 'react';

import { fetchMyNewsFeedSettings, updateMyNewsFeedSettings } from '../../../../actions/news_feed/news-feed-actions';
import type { NewsFeedSettingsOutput } from '../../../../utils/api-types';

export type NewsFeedType = NewsFeedSettingsOutput['news_feed_settings_subscribed_types'][number];

/** The news feed types a user can subscribe to, in display order. */
export const NEWS_FEED_TYPES: NewsFeedType[] = ['RESOURCE_SCENARIO'];

/** Preferences of the current user, loaded once and replaced by the server answer on each save. */
const useNewsFeedSettings = () => {
  const [settings, setSettings] = useState<NewsFeedSettingsOutput | null>(null);
  const [failed, setFailed] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);

  useEffect(() => {
    let cancelled = false;
    fetchMyNewsFeedSettings()
      .then((result) => {
        if (!cancelled) {
          setSettings(result.data);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setFailed(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // A failed save leaves the previous settings in place: the error itself is notified by the call.
  const updateSubscribedTypes = useCallback(async (subscribedTypes: NewsFeedType[]) => {
    setSaving(true);
    try {
      const result = await updateMyNewsFeedSettings({ news_feed_settings_subscribed_types: subscribedTypes });
      setSettings(result.data);
    } catch {
      // already notified
    } finally {
      setSaving(false);
    }
  }, []);

  return {
    settings,
    failed,
    saving,
    updateSubscribedTypes,
  };
};

export default useNewsFeedSettings;
