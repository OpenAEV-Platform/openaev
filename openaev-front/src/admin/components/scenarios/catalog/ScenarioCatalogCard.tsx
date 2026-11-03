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
  // Name what is missing (Nmap, Nuclei, Enterprise Edition) rather than a generic "requirements".
  const missingNames = (item.catalog_item_missing_prerequisites ?? [])
    // Short name for the chip ("Nmap: The network mapper" → "Nmap"); the tooltip keeps the full list.
    .map(prerequisite => (prerequisite.requirement_catalog_connector_title ?? prerequisite.requirement_label).split(':')[0].trim())
    .filter(name => name.length > 0);
  let needs = t('Requirements missing');
  if (missingNames.length === 1 || missingNames.length === 2) {
    needs = t('Needs {names}', { names: missingNames.join(', ') });
  } else if (missingNames.length > 2) {
    needs = t('Needs {count} prerequisites', { count: String(missingNames.length) });
  }

  const footer = (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      minWidth: 0,
    }}
    >
      {installed && <Chip label={t('Installed')} severity="low" />}
      {!installed && missing.length > 0 && (
        <Tooltip>
          <TooltipTrigger asChild>
            <span
              className="inline-flex"
              style={{
                minWidth: 0,
                maxWidth: '100%',
              }}
            >
              <Chip label={needs} severity="high" style={{ maxWidth: '100%' }} />
            </span>
          </TooltipTrigger>
          <TooltipContent>{missing.join(', ')}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );

  // Buttons the user has no right for are hidden, not greyed out without a reason (MPRD RBAC).
  const canInstallOrOpen = !!installed || canInstall;
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
      action={canInstallOrOpen && (
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
