function buildAllSimulationTabs(params: {
  lessonsEnabled: boolean | undefined;
  isAutonomous: boolean;
  hasWorkflow: boolean;
  t: (value: string) => string;
}): [string, string][] {
  const {
    lessonsEnabled,
    isAutonomous,
    hasWorkflow,
    t,
  } = params;

  if (isAutonomous) {
    return [
      ['', t('Overview')],
      ['/scope', t('Scope')],
      ['/logic', t('Logic')],
      ...(hasWorkflow ? [['/attack-path', t('Attack Path')] as [string, string]] : []),
      ['/execution', t('Execution')],
      ['/findings', t('Findings')],
      ['/graph', t('Graph')],
      ['/statistics', t('Statistics')],
    ];
  }

  if (hasWorkflow) {
    return [
      ['', t('Overview')],
      ['/scope', t('Scope')],
      ['/logic', t('Logic')],
      ['/execution', t('Execution')],
      ['/attack-path', t('Attack Path')],
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

export default function buildSimulationTabs(params: {
  lessonsEnabled: boolean | undefined;
  isAutonomous: boolean;
  hasWorkflow: boolean;
  // The Graph tab belongs to the exploration graph preview feature (off by default).
  graphEnabled?: boolean;
  t: (value: string) => string;
}): [string, string][] {
  const { graphEnabled = false } = params;
  return buildAllSimulationTabs(params).filter(([suffix]) => graphEnabled || suffix !== '/graph');
}
