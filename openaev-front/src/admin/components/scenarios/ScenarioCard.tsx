import { Button, Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type CatalogItemOutput, type Domain } from '../../../utils/api-types';
import handle from '../../../utils/period/Period';
import useScenarioPermissions from '../../../utils/permissions/useScenarioPermissions';
import ScenarioPopover from './scenario/ScenarioPopover';
import ScenarioStatus from './scenario/ScenarioStatus';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, ScenarioTypeIcon } from './scenario/ScenarioType';
import ScenarioUpdateDialog from './scenario/versions/ScenarioUpdateDialog';
import { sortedDomains } from './scenarioDomains';
import { type ActiveScenario } from './useActiveScenarios';
import useScenarioLaunch from './useScenarioLaunch';

interface Props {
  scenario: ActiveScenario;
  catalogItem?: CatalogItemOutput;
  onDelete: (scenarioId: string) => void;
}

const ScenarioCard: FunctionComponent<Props> = ({ scenario, catalogItem, onDelete }) => {
  const { t, nsd, locale } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const [updating, setUpdating] = useState(false);
  const { canLaunch, canManage } = useScenarioPermissions(scenario.scenario_id);
  const { launch: launchScenario, targetsDrawer } = useScenarioLaunch();
  const launch = () => launchScenario(scenario.scenario_id, scenario.scenario_name);
  const {
    scenario_workflow_id: workflowId,
    scenario_author_name: authorName,
    scenario_last_run_at: lastRunAt,
  } = scenario;
  const domainIds = scenario.scenario_domains ?? [];
  const allDomains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const primaryDomain = sortedDomains(allDomains.filter(domain => domainIds.includes(domain.domain_id)))[0];
  const version = scenario.scenario_version;
  const recurrence = useMemo(() => {
    const cron = scenario.scenario_recurrence ? handle(scenario.scenario_recurrence) : undefined;
    return cron?.isValid() ? cron.toTranslatableStringArray(locale).map(element => t(element)).join(' ') : undefined;
  }, [scenario.scenario_recurrence, locale]);
  const updateAvailable = !!catalogItem && !(catalogItem.catalog_item_installed_versions ?? []).includes(catalogItem.catalog_item_version);

  return (
    <>
      {updating && catalogItem && (
        <ScenarioUpdateDialog
          scenarioId={scenario.scenario_id}
          version={catalogItem.catalog_item_version}
          onClose={() => setUpdating(false)}
          onApplied={() => navigate(`/admin/scenarios/${scenario.scenario_id}`)}
        />
      )}
      {targetsDrawer}
      <MarketplaceCard
        accent={primaryDomain?.domain_color ?? theme.palette.primary.main}
        testId="scenario-card"
        icon={<ScenarioTypeIcon type={workflowId ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED} />}
        title={scenario.scenario_name}
        overline={primaryDomain ? t(primaryDomain.domain_name) : undefined}
        subtitle={authorName ? `${t('by')} ${authorName}` : undefined}
        description={scenario.scenario_description ?? undefined}
        linkTo={`/admin/scenarios/${scenario.scenario_id}`}
        topRight={(
          <ScenarioPopover
            scenario={scenario}
            actions={['Duplicate', 'Export', 'Delete']}
            onDelete={onDelete}
            inList
            // Launch on every Active card, installed or local (F6); Update only for a catalogue scenario.
            leadingEntries={[{
              label: 'Launch',
              action: () => {
                launch();
              },
              userRight: canLaunch,
            }, ...(updateAvailable
              ? [{
                  label: 'Update',
                  action: () => setUpdating(true),
                  userRight: canManage,
                }]
              : [])]}
          />
        )}
        chips={null}
        platforms={scenario.scenario_platforms ?? []}
        footerRight={(
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
          >
            {updateAvailable && <Chip label={t('Update available')} severity="medium" />}
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="inline-flex">
                  <ScenarioStatus scenario={scenario} variant="list" />
                </span>
              </TooltipTrigger>
              <TooltipContent>{recurrence ?? t('Not scheduled')}</TooltipContent>
            </Tooltip>
          </div>
        )}
        action={canLaunch && (
          <Button size="sm" onClick={launch}>{t('Launch')}</Button>
        )}
        caption={[
          version ? `v${version}` : null,
          lastRunAt ? `${t('Last run')} ${nsd(lastRunAt)}` : t('Never run'),
        ].filter(Boolean).join(' · ')}
      />
    </>
  );
};

export default ScenarioCard;
