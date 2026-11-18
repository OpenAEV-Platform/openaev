import { type FunctionComponent } from 'react';

import type { DomainHelper } from '../../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../../components/common/marketplace/card/MarketplaceCard';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Domain } from '../../../../utils/api-types';
import { domainsOf } from '../../../../utils/domains/domainUtils';
import { isFeatureEnabled } from '../../../../utils/utils';
import ScenarioPopover from '../scenario/ScenarioPopover';
import ScenarioStatus from '../scenario/ScenarioStatus';
import { ScenarioTypeIcon } from '../scenario/ScenarioType';
import { type ScenarioRow } from '../scenarioRow';

interface Props {
  scenario: ScenarioRow;
  onDelete: (scenarioId: string) => void;
}

const ScenarioCard: FunctionComponent<Props> = ({ scenario, onDelete }) => {
  const { t, nsd } = useFormatter();
  const allDomains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const primaryDomain = domainsOf(scenario.scenario_domains, allDomains)[0];
  const lastRunAt = scenario.scenario_last_run_at;
  const catalog = isFeatureEnabled('CATALOG');

  return (
    <MarketplaceCard
      testId="scenario-card"
      icon={<ScenarioTypeIcon scenario={scenario} />}
      overline={primaryDomain ? t(primaryDomain.domain_name) : undefined}
      title={scenario.scenario_name}
      subtitle={scenario.scenario_author_name ? `${t('by')} ${scenario.scenario_author_name}` : undefined}
      description={scenario.scenario_description}
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
        <ScenarioPopover
          scenario={scenario}
          actions={['Duplicate', 'Export', 'Delete']}
          onDelete={onDelete}
          inList
        />
      )}
      linkTo={`/admin/scenarios/${scenario.scenario_id}`}
    />
  );
};

export default ScenarioCard;
