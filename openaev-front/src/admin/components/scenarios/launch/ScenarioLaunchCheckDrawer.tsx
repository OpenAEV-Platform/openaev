import { Alert, Button, Drawer, DrawerBody, DrawerContent, DrawerFooter, DrawerHeader, Spinner, Text } from '@filigran/design-system';
import { PlayArrowOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import HealthcheckFixButton from '../../common/healthchecks/HealthcheckFixButton';
import isScopeLaunchBlocked from '../../common/healthchecks/scopeHealthcheck';
import MissingRequirementAlert from '../catalog/MissingRequirementAlert';
import { NO_TARGETS, type ScenarioTargets } from '../targets/scenarioTargets';
import { type LaunchTarget } from './launchTarget';
import RevokedInjectsFix from './RevokedInjectsFix';
import ScenarioLaunchTargetsDrawer from './ScenarioLaunchTargetsDrawer';
import useLaunchCheck, { type LaunchCheckState } from './useLaunchCheck';

interface Props {
  scenario: LaunchTarget;
  // Starts the simulation, with targets for it only.
  onLaunch: (targets: ScenarioTargets) => Promise<unknown>;
  onClose: () => void;
  // The check the opener already loaded and shows: the drawer reads and refreshes that one.
  sharedCheck?: LaunchCheckState;
}

const SHOWN_NAMES = 3;

// One check before every launch: each thing that blocks it or makes injects fail, with its fix.
const ScenarioLaunchCheckDrawer: FunctionComponent<Props> = ({ scenario, onLaunch, onClose, sharedCheck }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const countLabel = useCountLabel();
  const { canManage } = useScenarioPermissions(scenario.scenarioId);
  const ownCheck = useLaunchCheck(sharedCheck ? null : scenario.scenarioId);
  const { launchCheck, checking, checkAgain } = sharedCheck ?? ownCheck;
  const [settingTargets, setSettingTargets] = useState(false);
  const [launching, setLaunching] = useState(false);

  if (settingTargets && launchCheck) {
    return (
      <ScenarioLaunchTargetsDrawer
        scenarioId={scenario.scenarioId}
        name={scenario.name}
        injects={launchCheck.check.launch_check_injects}
        onLaunch={onLaunch}
        onClose={() => setSettingTargets(false)}
      />
    );
  }

  const names = (titles: string[]) => (titles.length > SHOWN_NAMES
    ? `${titles.slice(0, SHOWN_NAMES).join(', ')} ${t('and {count} more', { count: String(titles.length - SHOWN_NAMES) })}`
    : titles.join(', '));
  const blocking = launchCheck?.blocking ?? 0;
  const failing = launchCheck?.failing ?? 0;
  let summary = t('Ready to launch');
  if (blocking > 0) {
    summary = countLabel(blocking, '1 thing to fix before this scenario can launch', '{count} things to fix before this scenario can launch');
  } else if (failing > 0) {
    summary = countLabel(failing, 'Ready to launch, but 1 check may make injects fail', 'Ready to launch, but {count} checks may make injects fail');
  }

  const launch = () => {
    setLaunching(true);
    onLaunch(NO_TARGETS)
      .catch(() => undefined)
      .finally(() => setLaunching(false));
  };

  return (
    <Drawer open onOpenChange={next => !next && onClose()}>
      <DrawerContent size="md" busy={launching}>
        <DrawerHeader overline={t('Launch')} title={scenario.name} closeLabel={t('Close')} />
        <DrawerBody>
          {launchCheck === null
            ? <Spinner />
            : (
                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: theme.spacing(1),
                }}
                >
                  <Text variant="content-base-bold">{summary}</Text>
                  {launchCheck.revokedByAction.length > 0 && (
                    <>
                      <Alert
                        elevation={1}
                        severity="warning"
                        title={countLabel(launchCheck.check.launch_check_revoked_injects.length, '1 inject uses a revoked version', '{count} injects use a revoked version')}
                        description={`${names(launchCheck.check.launch_check_revoked_injects.map(inject => inject.inject_title))}. ${t('They will fail: move them to another version, or remove them.')}`}
                        action={canManage
                          ? (
                              <Button asChild size="sm" priority="secondary">
                                <Link to={`/admin/scenarios/${scenario.scenarioId}/injects`}>{t('Open the injects')}</Link>
                              </Button>
                            )
                          : undefined}
                      />
                      {canManage && launchCheck.revokedByAction.map(([actionId, injects]) => (
                        <RevokedInjectsFix key={actionId} actionId={actionId} injects={injects} onMoved={checkAgain} />
                      ))}
                    </>
                  )}
                  {launchCheck.untargeted.length > 0 && (
                    <Alert
                      elevation={1}
                      severity="error"
                      title={countLabel(launchCheck.untargeted.length, '1 inject has no target', '{count} injects have no target')}
                      description={`${names(launchCheck.untargeted.map(inject => inject.inject_title))}. ${t('Pick the assets, asset groups, teams or addresses they run against.')}`}
                      action={<Button type="button" size="sm" priority="secondary" onClick={() => setSettingTargets(true)}>{t('Set targets')}</Button>}
                    />
                  )}
                  {launchCheck.check.launch_check_missing_requirements.map(requirement => (
                    <MissingRequirementAlert key={requirement.requirement_label} requirement={requirement} severity="error" />
                  ))}
                  {launchCheck.healthchecks.map(healthcheck => (
                    <Alert
                      elevation={1}
                      key={`${healthcheck.type}-${healthcheck.detail}`}
                      severity={isScopeLaunchBlocked([healthcheck]) ? 'error' : 'warning'}
                      title={t(`healthcheck.type.${healthcheck.type}`)}
                      description={t(`healthcheck.description.${healthcheck.type}.${healthcheck.detail}`)}
                      action={(
                        <HealthcheckFixButton
                          healthcheck={healthcheck}
                          owner={{ scenarioId: scenario.scenarioId }}
                          priority="secondary"
                          readOnly={!canManage}
                        />
                      )}
                    />
                  ))}
                  {!canManage && (blocking > 0 || launchCheck.revokedByAction.length > 0) && (
                    <Alert
                      elevation={1}
                      severity="info"
                      title={t('Only someone who can edit this scenario can fix these')}
                      description={t('Ask a scenario owner or your administrator, then check again.')}
                    />
                  )}
                </div>
              )}
          {launchCheck !== null && (
            <DrawerFooter>
              {blocking > 0 && (
                <Text variant="content-caption" className="text-default-secondary" style={{ marginRight: 'auto' }}>
                  {t('Launch stays disabled until these are fixed.')}
                </Text>
              )}
              <Button type="button" priority="secondary" loading={checking} onClick={checkAgain}>{t('Check again')}</Button>
              <Button type="button" priority="secondary" disabled={launching} onClick={onClose}>{t('Cancel')}</Button>
              <Button
                type="button"
                loading={launching}
                disabled={blocking > 0 || checking}
                startIcon={<PlayArrowOutlined fontSize="small" />}
                onClick={launch}
              >
                {failing > 0 ? t('Launch anyway') : t('Launch now')}
              </Button>
            </DrawerFooter>
          )}
        </DrawerBody>
      </DrawerContent>
    </Drawer>
  );
};

export default ScenarioLaunchCheckDrawer;
