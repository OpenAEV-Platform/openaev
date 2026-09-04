import { Translate } from '../../../../components/i18n';

export default function buildSimulationTabs(params: {
  lessonsEnabled: boolean | undefined;
  isAutonomous: boolean;
  hasWorkflow: boolean;
  hasInjectTests: boolean;
  t: (value: string) => string;
}): [string, string][] {
  const {
    lessonsEnabled,
    isAutonomous,
    hasWorkflow,
    hasInjectTests,
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
      ['/statistics', t('Statistics')],
    ];
  }

  return [
    ['', t('Overview')],
    ['/injects', t('Injects')],
    ...(hasInjectTests ? [['/tests', t('Tests')] as [string, string]] : []),
    ['/execution', t('Execution')],
    ['/findings', t('Findings')],
    ...(lessonsEnabled ? [['/lessons', t('Lessons learned')] as [string, string]] : []),
    ['/statistics', t('Statistics')],
  ];
}
