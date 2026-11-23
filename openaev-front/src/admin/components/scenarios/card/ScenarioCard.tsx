import { Button, CardBanner } from '@filigran/design-system';
import { type FunctionComponent, type MouseEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import type { DomainHelper } from '../../../../actions/domains/domain-helper';
import { fetchScenarioRevisions } from '../../../../actions/scenarios/scenario-update-actions';
import MarketplaceCard from '../../../../components/common/marketplace/card/MarketplaceCard';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Domain, type ScenarioRevisionOutput } from '../../../../utils/api-types';
import { domainsOf } from '../../../../utils/domains/domainUtils';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { isFeatureEnabled } from '../../../../utils/utils';
import useScenarioLaunch from '../launch/useScenarioLaunch';
import ScenarioPopover from '../scenario/ScenarioPopover';
import ScenarioStatus from '../scenario/ScenarioStatus';
import { ScenarioTypeIcon } from '../scenario/ScenarioType';
import ScenarioUpdateDialog from '../scenario/versions/ScenarioUpdateDialog';
import { rollbackTarget, type ScenarioUpdateTarget, updateTarget } from '../scenario/versions/scenarioUpdateTargets';
import { type ScenarioRow, scenarioTypeOf } from '../scenarioRow';

interface Props {
  scenario: ScenarioRow;
  // The newer Filigran version of an installed catalogue scenario, when there is one.
  newerVersion?: string;
  onDelete: (scenarioId: string) => void;
}

const ScenarioCard: FunctionComponent<Props> = ({ scenario, newerVersion, onDelete }) => {
  const { t, nsd, fsd } = useFormatter();
  const navigate = useNavigate();
  const allDomains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const primaryDomain = domainsOf(scenario.scenario_domains, allDomains)[0];
  const lastRunAt = scenario.scenario_last_run_at;
  const catalog = isFeatureEnabled('CATALOG');
  const { canLaunch, canManage } = useScenarioPermissions(scenario.scenario_id);
  const { launch, launchDrawer } = useScenarioLaunch();
  const installedVersion = scenario.scenario_version;
  // Updating and rolling back change the content: for the people who can manage the scenario only.
  const canUpdate = catalog && canManage && !!installedVersion;
  const [target, setTarget] = useState<ScenarioUpdateTarget | null>(null);

  // Right after an update, its rollback is one click away in the card's menu; it never clutters it otherwise.
  const [lastUpdate, setLastUpdate] = useState<ScenarioRevisionOutput | null>(null);
  useEffect(() => {
    if (!canUpdate) return;
    fetchScenarioRevisions(scenario.scenario_id)
      .then(revisions => setLastUpdate(revisions[0]?.scenario_revision_reason === 'UPDATE' ? revisions[0] : null))
      .catch(() => setLastUpdate(null));
  }, [canUpdate, scenario.scenario_id, installedVersion]);

  // The card is a link: its banner action opens the update instead.
  const openUpdate = (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    if (newerVersion && installedVersion) setTarget(updateTarget(scenario.scenario_id, newerVersion, installedVersion, t));
  };
  const banner = canUpdate && newerVersion && newerVersion !== installedVersion && (
    <CardBanner
      severity="info"
      action={{
        label: t('Update'),
        onClick: openUpdate,
      }}
    >
      {t('Update available')}
    </CardBanner>
  );
  const rollbackEntries = lastUpdate && installedVersion
    ? [{
        label: t('Roll back last update'),
        action: () => setTarget(rollbackTarget(scenario.scenario_id, lastUpdate, installedVersion, t, fsd(lastUpdate.scenario_revision_created_at))),
        userRight: canUpdate,
      }]
    : [];

  return (
    <>
      <MarketplaceCard
        testId="scenario-card"
        icon={<ScenarioTypeIcon type={scenarioTypeOf(scenario)} />}
        overline={primaryDomain ? t(primaryDomain.domain_name) : undefined}
        title={scenario.scenario_name}
        subtitle={scenario.scenario_author_name ? `${t('by')} ${scenario.scenario_author_name}` : undefined}
        description={scenario.scenario_description}
        banner={banner || undefined}
        info={[
          // Only a scenario installed from the catalogue has a version.
          ...(catalog
            ? [{
                label: t('Version'),
                value: scenario.scenario_version ? `v${scenario.scenario_version}` : '–',
              }, {
                label: t('Source'),
                value: t(scenario.scenario_version ? 'Filigran' : 'Local'),
              }]
            : []),
          {
            label: t('Last run'),
            value: lastRunAt ? nsd(lastRunAt) : t('Never'),
          },
        ]}
        footerStart={<MarketplaceCardMeta status={<ScenarioStatus scenario={scenario} variant="list" />} platforms={scenario.scenario_platforms ?? []} />}
        action={(
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 4,
          }}
          >
            {catalog && canLaunch && (
              <Button
                size="sm"
                onClick={() => launch({
                  scenarioId: scenario.scenario_id,
                  name: scenario.scenario_name,
                  type: scenarioTypeOf(scenario),
                })}
              >
                {t('Launch')}
              </Button>
            )}
            <ScenarioPopover
              scenario={scenario}
              actions={['Duplicate', 'Export', 'Delete']}
              leadingEntries={rollbackEntries}
              onDelete={onDelete}
              inList
            />
          </div>
        )}
        linkTo={`/admin/scenarios/${scenario.scenario_id}`}
      />
      {launchDrawer}
      {/* After an update or a rollback, the scenario opens on what it now is. */}
      {target && (
        <ScenarioUpdateDialog
          target={target}
          onClose={() => setTarget(null)}
          onApplied={() => navigate(`/admin/scenarios/${scenario.scenario_id}`)}
        />
      )}
    </>
  );
};

export default ScenarioCard;
