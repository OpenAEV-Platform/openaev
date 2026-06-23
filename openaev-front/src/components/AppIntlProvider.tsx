import { LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import moment from 'moment';
import { type FunctionComponent, type ReactElement, useEffect, useState } from 'react';
import { IntlProvider } from 'react-intl';

import { type LoggedHelper } from '../actions/helper';
import { DEFAULT_LANG } from '../constants/Lang';
import { useHelper } from '../store';
import { dateFnsLocaleMap, type LanguageCode, loadLocaleMessages, momentMap, oaevLocaleMap } from '../utils/locales';

// Export LANG to be used in non-React code
// eslint-disable-next-line import/no-mutable-exports
export let LANG = DEFAULT_LANG;

const AppIntlProvider: FunctionComponent<{ children: ReactElement }> = ({ children }) => {
  const { platformName, userLang }: {
    platformName: string;
    userLang: LanguageCode;
  } = useHelper((helper: LoggedHelper) => {
    const platformName = helper.getPlatformName();
    const userLang = helper.getUserLang();

    return {
      platformName,
      userLang,
    };
  });

  LANG = userLang;
  // Locales other than the default are code-split: render with the best catalog already in
  // memory and swap in the requested one as soon as its chunk is loaded
  const [baseMessages, setBaseMessages] = useState<Record<string, string>>(
    () => oaevLocaleMap[userLang] ?? oaevLocaleMap[DEFAULT_LANG as LanguageCode] ?? oaevLocaleMap.en,
  );
  useEffect(() => {
    let cancelled = false;
    loadLocaleMessages(userLang).then((messages) => {
      if (!cancelled) {
        setBaseMessages(messages);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [userLang]);
  const momentLocale = momentMap[userLang];
  moment.locale(momentLocale);
  useEffect(() => {
    document.title = platformName;
  }, [platformName]);

  return (
    <IntlProvider
      locale={userLang}
      defaultLocale={DEFAULT_LANG}
      key={userLang}
      messages={baseMessages}
      onError={(err) => {
        if (err.code === 'MISSING_TRANSLATION') {
          return;
        }
        throw err;
      }}
    >
      <LocalizationProvider
        dateAdapter={AdapterDateFns}
        adapterLocale={dateFnsLocaleMap[userLang]}
      >
        {children}
      </LocalizationProvider>
    </IntlProvider>
  );
};

export default AppIntlProvider;
