export const themeItems = (t: (text: string) => string) => [
  {
    value: 'default',
    label: t('Default'),
  },
  {
    value: 'dark',
    label: t('Dark'),
  },
  {
    value: 'light',
    label: t('Light'),
  },
];

export const langItems = (t: (text: string) => string) => [
  {
    value: 'auto',
    label: t('Automatic'),
  },
  {
    value: 'de',
    label: t('German'),
  },
  {
    value: 'en',
    label: t('English'),
  },
  {
    value: 'es',
    label: t('Spanish'),
  },
  {
    value: 'fr',
    label: t('French'),
  },
  {
    value: 'it',
    label: t('Italian'),
  },
  {
    value: 'ja',
    label: t('Japanese'),
  },
  {
    value: 'ko',
    label: t('Korean'),
  },
  {
    value: 'ru',
    label: t('Russian'),
  },
  {
    value: 'zh',
    label: t('Chinese'),
  },
];
