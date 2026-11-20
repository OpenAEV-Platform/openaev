function buildAllScenarioTabs(params: {
  isChained: boolean;
  lessonsEnabled: boolean | undefined;
  t: (value: string) => string;
}): [string, string][] {
  const {
    isChained,
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
      ['/findings', t('Findings')],
      ...(lessonsEnabled ? [['/lessons', t('Lessons learned')] as [string, string]] : []),
      ['/graph', t('Graph')],
      ['/statistics', t('Statistics')],
    ];
  }

  return [
    ['', t('Overview')],
    ['/injects', t('Injects')],
    ['/tests', t('Tests')],
    ['/execution', t('Execution')],
    ['/findings', t('Findings')],
    ...(lessonsEnabled ? [['/lessons', t('Lessons learned')] as [string, string]] : []),
    ['/graph', t('Graph')],
    ['/statistics', t('Statistics')],
  ];
}

export default function buildScenarioTabs(params: {
  isChained: boolean;
  lessonsEnabled: boolean | undefined;
  // The Graph tab belongs to the exploration graph preview feature (off by default).
  graphEnabled?: boolean;
  t: (value: string) => string;
}): [string, string][] {
  const { graphEnabled = false } = params;
  return buildAllScenarioTabs(params).filter(([suffix]) => graphEnabled || suffix !== '/graph');
}
