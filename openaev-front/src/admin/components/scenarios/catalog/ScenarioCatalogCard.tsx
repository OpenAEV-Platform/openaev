import { Button, Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent } from 'react';
import { Link } from 'react-router';

import MarketplaceCard from '../../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../../components/i18n';
import { type CatalogItemOutput } from '../../../../utils/api-types';
import { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, ScenarioTypeIcon } from '../scenario/ScenarioType';

interface Props {
  item: CatalogItemOutput;
  canInstall: boolean;
  canLaunch: boolean;
  onOpen: () => void;
  onInstall: () => void;
  onLaunch: () => void;
}

// Links and buttons on the card do their own thing, not open the drawer.
const stop = (event: MouseEvent) => event.stopPropagation();

const ScenarioCatalogCard: FunctionComponent<Props> = ({ item, canInstall, canLaunch, onOpen, onInstall, onLaunch }) => {
  const { t, nsd } = useFormatter();
  const theme = useTheme();
  // The newest installed version: one instance per scenario.
  const installed = (item.catalog_item_installed_scenarios ?? [])[0];
  const missing = item.catalog_item_missing_requirements ?? [];

  const footer = (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
    }}
    >
      {installed && <Chip label={t('Installed')} severity="low" />}
      {!installed && missing.length > 0 && (
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="inline-flex">
              <Chip label={t('Requirements missing')} severity="high" />
            </span>
          </TooltipTrigger>
          <TooltipContent>{missing.join(', ')}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );

  return (
    <MarketplaceCard
      accent={theme.palette.primary.main}
      testId="scenario-catalog-card"
      icon={<ScenarioTypeIcon type={item.catalog_item_scenario_type === 'chained' ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED} />}
      title={item.catalog_item_name ?? item.catalog_item_external_id}
      subtitle={item.catalog_item_author ? `${t('by')} ${item.catalog_item_author}` : undefined}
      description={item.catalog_item_description ?? undefined}
      chips={null}
      platforms={item.catalog_item_platforms ?? []}
      onClick={onOpen}
      footerRight={footer}
      action={(
        <div style={{
          display: 'flex',
          gap: 8,
        }}
        >
          {installed
            ? (
                <Button asChild size="sm" priority="secondary">
                  <Link to={`/admin/scenarios/${installed.scenario_id}`} onClick={stop}>{t('Open')}</Link>
                </Button>
              )
            : (
                <Button
                  size="sm"
                  priority="secondary"
                  onClick={(event) => {
                    stop(event);
                    onInstall();
                  }}
                  disabled={!canInstall}
                >
                  {t('Install')}
                </Button>
              )}
          {canLaunch && (
            // A scenario not installed yet is installed first.
            <Button
              size="sm"
              onClick={(event) => {
                stop(event);
                onLaunch();
              }}
              disabled={!installed && !canInstall}
            >
              {t('Launch')}
            </Button>
          )}
        </div>
      )}
      caption={[
        `v${item.catalog_item_version}`,
        item.catalog_item_injects_count != null ? `${item.catalog_item_injects_count} ${t('injects')}` : null,
        item.catalog_item_published_at ? `${t('Published')} ${nsd(item.catalog_item_published_at)}` : null,
      ].filter(Boolean).join(' · ')}
    />
  );
};

export default ScenarioCatalogCard;
