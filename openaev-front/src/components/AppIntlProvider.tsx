import 'cronstrue/locales/fr';
import 'cronstrue/locales/en';
import 'cronstrue/locales/es';
import 'cronstrue/locales/zh_CN';

import { LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { enUS as dateFnsEnUSLocale, es as dateFnsEsLocale, fr as dateFnsFrLocale, zhCN as dateFnsZhCNLocale } from 'date-fns/locale';
import moment from 'moment';
import { type FunctionComponent, type ReactElement, useEffect } from 'react';
import { IntlProvider } from 'react-intl';

import { type LoggedHelper } from '../actions/helper';
import { DEFAULT_LANG } from '../constants/Lang';
import { useHelper } from '../store';
import enOpenAEV from '../utils/lang/en.json';
import esOpenAEV from '../utils/lang/es.json';
import frOpenAEV from '../utils/lang/fr.json';
import zhOpenAEV from '../utils/lang/zh.json';

type Lang = 'en' | 'es' | 'fr' | 'zh';

const dateFnsLocaleMap = {
  en: dateFnsEnUSLocale,
  es: dateFnsEsLocale,
  fr: dateFnsFrLocale,
  zh: dateFnsZhCNLocale,
};

const oaevLocaleMap = {
  en: enOpenAEV,
  es: esOpenAEV,
  fr: frOpenAEV,
  zh: zhOpenAEV,
};

const momentMap = {
  en: 'en-us',
  es: 'es-es',
  fr: 'fr-fr',
  zh: 'zh-cn',
};

// Export LANG to be used in non-React code
// eslint-disable-next-line import/no-mutable-exports
export let LANG = DEFAULT_LANG;

const AppIntlProvider: FunctionComponent<{ children: ReactElement }> = ({ children }) => {
  const { platformName, userLang }: {
    platformName: string;
    userLang: Lang;
  } = useHelper((helper: LoggedHelper) => {
    const platformName = helper.getPlatformName();
    const userLang = helper.getUserLang();

    return {
      platformName,
      userLang,
    };
  });

  LANG = userLang;
  const baseMessages: Record<string, string> = oaevLocaleMap[userLang] || oaevLocaleMap[DEFAULT_LANG];
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
