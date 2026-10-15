import { Button, Chip } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import MarketplaceCard from '../../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../../components/i18n';
import ItemSeverity from '../../../../components/ItemSeverity';
import { type CatalogItemOutput } from '../../../../utils/api-types';
import { SCENARIO_TYPE_LABELS, scenarioCategories } from '../constants';
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
  const scenarioType = item.catalog_item_scenario_type;
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
        <Button size="sm" priority="secondary" onClick={onInstall} disabled={!canInstall}>
          {t('Install')}
        </Button>
      </div>
    );
  }

  return (
    <MarketplaceCard
      accent={severityAccent(theme, severity)}
      testId="scenario-catalog-card"
      icon={<RouteOutlined color="primary" />}
      title={item.catalog_item_name ?? item.catalog_item_external_id}
      subtitle={item.catalog_item_author ? `${t('by')} ${item.catalog_item_author}` : undefined}
      chips={(
        <>
          {severity && <ItemSeverity label={t(severityLabelKey(severity))} severity={severity} />}
          {category && <Chip label={t(scenarioCategories.get(category) ?? category)} />}
          {scenarioType && <Chip label={t(SCENARIO_TYPE_LABELS[scenarioType] ?? scenarioType)} />}
        </>
      )}
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
