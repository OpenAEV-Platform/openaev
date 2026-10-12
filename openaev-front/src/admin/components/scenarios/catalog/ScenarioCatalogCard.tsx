import { Button, Chip } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { type CatalogScenario } from '../../../../actions/catalog/scenario-catalog-preview';
import MarketplaceCard from '../../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../../components/i18n';
import ItemSeverity from '../../../../components/ItemSeverity';
import { scenarioCategories } from '../constants';
import { severityAccent, severityLabelKey } from '../scenarioSeverity';

interface Props {
  item: CatalogScenario;
  installed: boolean;
  canInstall: boolean;
  onInstall: () => void;
}

const ScenarioCatalogCard: FunctionComponent<Props> = ({ item, installed, canInstall, onInstall }) => {
  const { t, nsd } = useFormatter();
  const theme = useTheme();
  const accent = severityAccent(theme, item.catalog_item_severity);

  return (
    <MarketplaceCard
      accent={accent}
      testId="scenario-catalog-card"
      icon={<RouteOutlined color="primary" />}
      title={item.catalog_item_name}
      subtitle={`${t('by')} ${item.catalog_item_author}`}
      chips={(
        <>
          <ItemSeverity label={t(severityLabelKey(item.catalog_item_severity))} severity={item.catalog_item_severity} />
          <Chip label={t(scenarioCategories.get(item.catalog_item_category) ?? item.catalog_item_category)} />
          <Chip label={t(item.catalog_item_type)} />
        </>
      )}
      platforms={item.catalog_item_platforms}
      footerRight={installed
        ? <Chip label={t('Installed')} severity="low" />
        : (
            <Button size="sm" priority="secondary" onClick={onInstall} disabled={!canInstall}>
              {t('Install')}
            </Button>
          )}
      caption={`v${item.catalog_item_version} · ${item.catalog_item_injects_count} ${t('injects')} · ${t('Published')} ${nsd(item.catalog_item_published_at)}`}
    />
  );
};

export default ScenarioCatalogCard;
