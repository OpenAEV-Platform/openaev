export default function buildScenarioTabs(params: {
  isChained: boolean;
  lessonsEnabled: boolean | undefined;
  isCatalog?: boolean;
  t: (value: string) => string;
}): [string, string][] {
  const {
    isChained,
    lessonsEnabled,
    isCatalog = false,
    t,
  } = params;
  const versions: [string, string][] = isCatalog ? [['/versions', t('Versions')]] : [];

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
      ...versions,
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
    ...versions,
  ];
}
