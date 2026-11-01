import { Alert, Button, Spinner, Text } from '@filigran/design-system';
import { PlayArrowOutlined } from '@mui/icons-material';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';
import { Link } from 'react-router';

import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type Scenario } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import healthcheckFixLink from '../../common/healthchecks/healthcheckFixLink';
import ScenarioLaunchTargetsDrawer from '../catalog/ScenarioLaunchTargetsDrawer';
import RevokedPayloadFix from './RevokedPayloadFix';
import loadScenarioLaunchBlockers, { hardBlockerCount, type RevokedInject, type ScenarioLaunchBlockers } from './scenarioLaunchBlockers';

interface Props {
  scenario: Scenario;
  open: boolean;
  onClose: () => void;
  // Starts the simulation; the caller decides where to go next.
  onLaunch: () => Promise<unknown>;
}

const SHOWN_NAMES = 3;

// One "can't launch yet" check for every launch: each blocker says why, with its fix in place.
const ScenarioLaunchCheckDrawer: FunctionComponent<Props> = ({ scenario, open, onClose, onLaunch }) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const { canManage } = useScenarioPermissions(scenario.scenario_id);
  const canManageIntegrations = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const [blockers, setBlockers] = useState<ScenarioLaunchBlockers | null>(null);
  const [checking, setChecking] = useState(false);
  const [launching, setLaunching] = useState(false);
  const [settingTargets, setSettingTargets] = useState(false);

  const check = () => {
    setChecking(true);
    loadScenarioLaunchBlockers(scenario)
      .then(setBlockers)
      .finally(() => setChecking(false));
  };
  useEffect(() => {
    if (open) {
      setBlockers(null);
      check();
    }
  }, [open, scenario.scenario_id]);

  if (settingTargets) {
    return (
      <ScenarioLaunchTargetsDrawer
        scenarioId={scenario.scenario_id}
        scenarioName={scenario.scenario_name}
        onClose={() => {
          setSettingTargets(false);
          onClose();
        }}
      />
    );
  }

  const names = (list: string[]) => (list.length > SHOWN_NAMES
    ? `${list.slice(0, SHOWN_NAMES).join(', ')} ${t('and {count} more', { count: String(list.length - SHOWN_NAMES) })}`
    : list.join(', '));
  // Fixes need the right to edit the scenario; without it the blocker stays visible, read-only.
  const fix = (action: ReactNode) => (canManage ? action : undefined);

  const revokedGroups = Array.from((blockers?.revokedInjects ?? []).reduce((groups, inject) => {
    if (inject.contractId) groups.set(inject.contractId, [...(groups.get(inject.contractId) ?? []), inject]);
    return groups;
  }, new Map<string, RevokedInject[]>()).entries());
  const hard = blockers ? hardBlockerCount(blockers) : 0;
  const soft = blockers?.healthchecks.length ?? 0;
  let summary = t('Ready to launch');
  if (hard > 0) {
    summary = hard === 1 ? t('1 thing to fix before this scenario can launch') : t('{count} things to fix before this scenario can launch', { count: String(hard) });
  } else if (soft > 0) {
    summary = soft === 1 ? t('Ready to launch, but 1 check may make injects fail') : t('Ready to launch, but {count} checks may make injects fail', { count: String(soft) });
  }
  let launchLabel = t('Launch now');
  if (soft > 0 && hard === 0) launchLabel = t('Launch anyway');

  const launch = () => {
    setLaunching(true);
    onLaunch().finally(() => setLaunching(false));
  };

  return (
    <Drawer open={open} handleClose={onClose} title={`${t('Launch')} ${scenario.scenario_name}`}>
      {blockers === null
        ? <Spinner />
        : (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 16,
            }}
            >
              <Text variant="content-base-bold">{summary}</Text>
              {blockers.revokedInjects.length > 0 && (
                <Alert
                  severity="error"
                  title={blockers.revokedInjects.length === 1
                    ? t('1 inject runs a revoked payload')
                    : t('{count} injects run a revoked payload', { count: String(blockers.revokedInjects.length) })}
                  description={(
                    <div style={{
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 8,
                    }}
                    >
                      <span>{`${names(blockers.revokedInjects.map(inject => inject.title))}. ${t('Move them to a payload version that is not revoked, or remove them.')}`}</span>
                      {/* One version picker per revoked payload version, right in the check (F18). */}
                      {canManage && revokedGroups.map(([contractId, injects]) => (
                        <RevokedPayloadFix key={contractId} contractId={contractId} injects={injects} onMoved={check} />
                      ))}
                    </div>
                  )}
                  action={fix(
                    <Button asChild size="sm" priority="secondary">
                      <Link to={`/admin/scenarios/${scenario.scenario_id}/injects`}>{t('Open the injects')}</Link>
                    </Button>,
                  )}
                />
              )}
              {blockers.untargetedInjects.length > 0 && (
                <Alert
                  severity="error"
                  title={blockers.untargetedInjects.length === 1
                    ? t('1 inject has no target')
                    : t('{count} injects have no target', { count: String(blockers.untargetedInjects.length) })}
                  description={`${names(blockers.untargetedInjects)}. ${t('Pick the assets, asset groups, teams or addresses they run against.')}`}
                  action={fix(
                    <Button size="sm" priority="secondary" onClick={() => setSettingTargets(true)}>{t('Set targets')}</Button>,
                  )}
                />
              )}
              {blockers.prerequisites.map(prerequisite => (
                <Alert
                  key={`${prerequisite.requirement_kind}:${prerequisite.requirement_key}`}
                  severity="error"
                  title={prerequisite.requirement_status === 'NOT_RUNNING'
                    ? t('{name} is not running', { name: prerequisite.requirement_catalog_connector_title ?? prerequisite.requirement_label })
                    : t('{name} is missing on this platform', { name: prerequisite.requirement_catalog_connector_title ?? prerequisite.requirement_label })}
                  description={t('The injects that need it cannot run.')}
                  action={prerequisite.requirement_kind === 'INJECTOR' && prerequisite.requirement_catalog_connector_id && canManageIntegrations
                    ? (
                        <Button asChild size="sm" priority="secondary">
                          <Link to={`/admin/integrations/catalog/${prerequisite.requirement_catalog_connector_id}`} target="_blank">
                            {t('Enable integration')}
                          </Link>
                        </Button>
                      )
                    : undefined}
                />
              ))}
              {blockers.healthchecks.map((healthcheck, index) => {
                const link = healthcheckFixLink(healthcheck.type, { scenarioId: scenario.scenario_id });
                const label = t(`healthcheck.button.${healthcheck.type}.${healthcheck.detail}`);
                let action: ReactNode;
                if (link.to) {
                  action = fix(
                    <Button asChild size="sm" priority="secondary">
                      <Link to={link.to}>{label}</Link>
                    </Button>,
                  );
                } else if (link.href) {
                  action = (
                    <Button asChild size="sm" priority="secondary">
                      <a href={link.href} target="_blank" rel="noopener noreferrer">{label}</a>
                    </Button>
                  );
                }
                return (
                  <Alert
                    key={`${healthcheck.type}-${healthcheck.detail}-${index}`}
                    severity="warning"
                    title={t(`healthcheck.type.${healthcheck.type}`)}
                    description={t(`healthcheck.description.${healthcheck.type}.${healthcheck.detail}`)}
                    action={action}
                  />
                );
              })}
              {!canManage && hard > 0 && (
                <Alert
                  severity="info"
                  title={t('Only someone who can edit this scenario can fix these')}
                  description={t('Ask a scenario owner or your administrator, then check again.')}
                />
              )}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-end',
                gap: 8,
              }}
              >
                {hard > 0 && <Text variant="content-caption" style={{ marginRight: 'auto' }}>{t('Launch stays disabled until these are fixed.')}</Text>}
                <Button priority="secondary" disabled={checking} onClick={check}>
                  {checking ? <Spinner size="sm" /> : t('Check again')}
                </Button>
                <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
                <Button
                  onClick={launch}
                  disabled={hard > 0 || launching || checking}
                  startIcon={<PlayArrowOutlined fontSize="small" />}
                  data-testid="scenario-launch-confirm"
                >
                  {launchLabel}
                </Button>
              </div>
            </div>
          )}
    </Drawer>
  );
};

export default ScenarioLaunchCheckDrawer;
