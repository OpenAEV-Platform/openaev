import { Button, Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import MarketplaceCard from '../../../../components/common/marketplace/card/MarketplaceCard';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import { useFormatter } from '../../../../components/i18n';
import { type CatalogScenarioOutput } from '../../../../utils/api-types';
import { scenarioCategories } from '../constants';
import { ScenarioTypeIcon } from '../scenario/ScenarioType';
import { catalogScenarioType } from './catalogScenarioFilters';

interface Props {
  scenario: CatalogScenarioOutput;
  canInstall: boolean;
  onInstall: () => void;
  onOpen: () => void;
}

const ScenarioCatalogCard: FunctionComponent<Props> = ({ scenario, canInstall, onInstall, onOpen }) => {
  const { t, nsd } = useFormatter();
  const category = scenario.catalog_scenario_category;
  const missing = scenario.catalog_scenario_missing_requirements.length > 0;

  return (
    <MarketplaceCard
      testId="scenario-catalog-card"
      icon={<ScenarioTypeIcon type={catalogScenarioType(scenario)} />}
      overline={category ? t(scenarioCategories.get(category) ?? category) : undefined}
      title={scenario.catalog_scenario_name ?? scenario.catalog_scenario_external_id}
      subtitle={scenario.catalog_scenario_author ? `${t('by')} ${scenario.catalog_scenario_author}` : undefined}
      description={scenario.catalog_scenario_description}
      onClick={onOpen}
      info={[
        {
          label: t('Version'),
          value: `v${scenario.catalog_scenario_version}`,
        },
        {
          label: t('Published'),
          value: scenario.catalog_scenario_published_at ? nsd(scenario.catalog_scenario_published_at) : '–',
        },
      ]}
      footerStart={(
        <MarketplaceCardMeta
          status={missing ? <Chip label={t('Requirements missing')} severity="high" /> : undefined}
          platforms={scenario.catalog_scenario_platforms}
        />
      )}
      action={canInstall && <Button size="sm" disabled={missing} onClick={onInstall}>{t('Install')}</Button>}
    />
  );
};

export default ScenarioCatalogCard;
