import { useCallback, useEffect, useState } from 'react';

import { fetchMyNewsFeedSettings, updateMyNewsFeedSettings } from '../../../../actions/news_feed/news-feed-actions';
import { useFormatter } from '../../../../components/i18n';
import type { NewsFeedSettingsOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { type Error as ApiError, notifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';

export type NewsFeedType = NewsFeedSettingsOutput['news_feed_settings_subscribed_types'][number];

/** The news feed types a user can subscribe to, in display order. */
export const NEWS_FEED_TYPES: NewsFeedType[] = ['RESOURCE_SCENARIO'];

/** Preferences of the current user, loaded once and replaced by the server answer on each save. */
const useNewsFeedSettings = () => {
  const { t } = useFormatter();
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

  // A failed save leaves the previous settings in place. The server refuses it with a 403 when the product
  // was disconnected from XTM Hub since the page loaded, which the generic message would not explain.
  const updateSubscribedTypes = useCallback(async (subscribedTypes: NewsFeedType[]) => {
    setSaving(true);
    try {
      const result = await updateMyNewsFeedSettings({ news_feed_settings_subscribed_types: subscribedTypes }, false);
      setSettings(result.data);
    } catch (error) {
      if ((error as ApiError).status === 403) {
        MESSAGING$.notifyError(t('Your OpenAEV product is no longer connected to XTM Hub'));
      } else {
        notifyErrorHandler(error as ApiError);
      }
    } finally {
      setSaving(false);
    }
  }, [t]);

  return {
    settings,
    failed,
    saving,
    updateSubscribedTypes,
  };
};

export default useNewsFeedSettings;
