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
    value: 'en',
    label: t('English'),
  },
  {
    value: 'fr',
    label: t('French'),
  },
  {
    value: 'zh',
    label: t('Chinese'),
  },
];
