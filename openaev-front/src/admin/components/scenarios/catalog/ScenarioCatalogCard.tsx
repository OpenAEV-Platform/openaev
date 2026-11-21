import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import MarketplaceCard from '../../../../components/common/marketplace/card/MarketplaceCard';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { ScenarioTypeIcon } from '../scenario/ScenarioType';
import CatalogScenarioAction from './CatalogScenarioAction';
import { catalogScenarioType } from './catalogScenarioType';
import useMissingRequirementsLabel from './useMissingRequirementsLabel';

interface Props {
  scenario: CatalogScenarioOutput;
  canInstall: boolean;
  onInstall: () => void;
  // Installs the scenario, then launches it; left out when the user may not launch.
  onLaunch?: () => void;
  onOpen: () => void;
}

const ScenarioCatalogCard: FunctionComponent<Props> = ({ scenario, canInstall, onInstall, onLaunch, onOpen }) => {
  const { t, nsd } = useFormatter();
  const missing = scenario.catalog_scenario_missing_requirements.length > 0;
  const installed = !!scenario.catalog_scenario_installed_scenario_id;
  const needs = useMissingRequirementsLabel(scenario.catalog_scenario_missing_requirements);

  return (
    <MarketplaceCard
      testId="scenario-catalog-card"
      icon={<ScenarioTypeIcon type={catalogScenarioType(scenario)} />}
      title={scenario.catalog_scenario_name ?? scenario.catalog_scenario_external_id}
      subtitle={scenario.catalog_scenario_author ? `${t('by')} ${scenario.catalog_scenario_author}` : undefined}
      description={scenario.catalog_scenario_description}
      onClick={onOpen}
      info={[
        {
          label: t('Version'),
          value: `v${scenario.catalog_scenario_version}`,
        },
        ...(scenario.catalog_scenario_injects_count != null
          ? [{
              label: t('Injects'),
              value: String(scenario.catalog_scenario_injects_count),
            }]
          : []),
        {
          label: t('Published'),
          value: scenario.catalog_scenario_published_at ? nsd(scenario.catalog_scenario_published_at) : '–',
        },
      ]}
      footerStart={(
        <MarketplaceCardMeta
          status={installed
            ? <Chip label={t('Installed')} severity="low" />
            : missing && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Chip label={needs.label} severity="high" style={{ maxWidth: '100%' }} />
                </TooltipTrigger>
                <TooltipContent>{needs.names}</TooltipContent>
              </Tooltip>
            )}
          platforms={scenario.catalog_scenario_platforms}
        />
      )}
      action={<CatalogScenarioAction scenario={scenario} canInstall={canInstall} onInstall={onInstall} onLaunch={onLaunch} size="sm" />}
    />
  );
};

export default ScenarioCatalogCard;
