export default function buildScenarioTabs(params: {
  isChained: boolean;
  lessonsEnabled: boolean | undefined;
  fromCatalog: boolean;
  t: (value: string) => string;
}): [string, string][] {
  const {
    isChained,
    lessonsEnabled,
    fromCatalog,
    t,
  } = params;
  const historyTab: [string, string][] = fromCatalog ? [['/history', t('History')]] : [];

  if (isChained) {
    return [
      ['', t('Overview')],
      ['/scope', t('Scope')],
      ['/logic', t('Logic')],
      ['/attack-path', t('Attack Path')],
      ['/execution', t('Execution')],
      ['/findings', t('Findings')],
      ...(lessonsEnabled ? [['/lessons', t('Lessons learned')] as [string, string]] : []),
      ['/statistics', t('Statistics')],
      ...historyTab,
    ];
  }

  return [
    ['', t('Overview')],
    ['/injects', t('Injects')],
    ['/tests', t('Tests')],
    ['/execution', t('Execution')],
    ['/findings', t('Findings')],
    ...(lessonsEnabled ? [['/lessons', t('Lessons learned')] as [string, string]] : []),
    ['/statistics', t('Statistics')],
    ...historyTab,
  ];
}
