export default function buildScenarioTabs(params: {
  isChained: boolean;
  hasInjectTests: boolean;
  lessonsEnabled: boolean | undefined;
  t: (value: string) => string;
}): [string, string][] {
  const {
    isChained,
    hasInjectTests,
    lessonsEnabled,
    t,
  } = params;

  if (isChained) {
    return [
      ['', t('Overview')],
      ['/scope', t('Scope')],
      ['/logic', t('Logic')],
      ['/attack-path', t('Attack Path')],
      ['/execution', t('Execution')],
      ...(lessonsEnabled ? [['/lessons', t('Lessons learned')] as [string, string]] : []),
      ['/findings', t('Findings')],
      ['/statistics', t('Statistics')],
    ];
  }

  return [
    ['', t('Overview')],
    ['/injects', t('Injects')],
    ...(hasInjectTests ? [['/tests', t('Tests')] as [string, string]] : []),
    ['/execution', t('Execution')],
    ...(lessonsEnabled ? [['/lessons', t('Lessons learned')] as [string, string]] : []),
    ['/findings', t('Findings')],
    ['/statistics', t('Statistics')],
  ];
}
