import { Button, Chip } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import MarketplaceCard from '../../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../../components/i18n';
import ItemSeverity from '../../../../components/ItemSeverity';
import { type CatalogItemOutput } from '../../../../utils/api-types';
import { scenarioCategories } from '../constants';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, ScenarioTypeIcon } from '../scenario/ScenarioType';
import { severityAccent, severityLabelKey } from '../scenarioSeverity';

interface Props {
  item: CatalogItemOutput;
  canInstall: boolean;
  onInstall: () => void;
}

const ScenarioCatalogCard: FunctionComponent<Props> = ({ item, canInstall, onInstall }) => {
  const { t, nsd } = useFormatter();
  const theme = useTheme();
  const severity = item.catalog_item_severity;
  const category = item.catalog_item_category;
  const installedVersions = item.catalog_item_installed_versions ?? [];
  const latestInstalled = installedVersions.includes(item.catalog_item_version);

  let footer;
  if (latestInstalled) {
    footer = <Chip label={t('Installed')} severity="low" />;
  } else {
    footer = (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
      }}
      >
        {installedVersions.length > 0 && <Chip label={t('Update available')} severity="medium" />}
        {(item.catalog_item_missing_requirements ?? []).length > 0 && (
          <Chip label={t('Requirements missing')} severity="high" title={(item.catalog_item_missing_requirements ?? []).join(', ')} />
        )}
        <Button size="sm" onClick={onInstall} disabled={!canInstall}>
          {t('Install')}
        </Button>
      </div>
    );
  }

  return (
    <MarketplaceCard
      accent={severityAccent(theme, severity)}
      testId="scenario-catalog-card"
      icon={<ScenarioTypeIcon type={item.catalog_item_scenario_type === 'chained' ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED} />}
      title={item.catalog_item_name ?? item.catalog_item_external_id}
      overline={category ? t(scenarioCategories.get(category) ?? category) : undefined}
      subtitle={item.catalog_item_author ? `${t('by')} ${item.catalog_item_author}` : undefined}
      description={item.catalog_item_description ?? undefined}
      chips={severity && <ItemSeverity label={t(severityLabelKey(severity))} severity={severity} />}
      platforms={item.catalog_item_platforms ?? []}
      footerRight={footer}
      caption={[
        `v${item.catalog_item_version}`,
        item.catalog_item_injects_count != null ? `${item.catalog_item_injects_count} ${t('injects')}` : null,
        item.catalog_item_published_at ? `${t('Published')} ${nsd(item.catalog_item_published_at)}` : null,
      ].filter(Boolean).join(' · ')}
    />
  );
};

export default ScenarioCatalogCard;
