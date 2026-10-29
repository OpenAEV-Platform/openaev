import { Button, Chip, Text } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type CatalogItemOutput } from '../../../../utils/api-types';
import ScenarioForm, { type ScenarioFormInput } from '../ScenarioForm';

interface Props {
  item: CatalogItemOutput | null;
  canInstall: boolean;
  onClose: () => void;
  onInstall: (item: CatalogItemOutput) => void;
}

const Section = ({ title, children }: {
  title: string;
  children: ReactNode;
}) => (
  <div style={{
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  }}
  >
    <Text variant="title-xs">{title}</Text>
    {children}
  </div>
);

// A scenario of the Available tab: what it runs and needs, before installing it.
const ScenarioCatalogDrawer: FunctionComponent<Props> = ({ item, canInstall, onClose, onInstall }) => {
  const { t, nsd } = useFormatter();
  if (!item) {
    return null;
  }
  const installed = (item.catalog_item_installed_scenarios ?? [])[0];
  const missing = item.catalog_item_missing_prerequisites ?? [];
  const payloads = item.catalog_item_payloads ?? [];
  const initialValues = {
    scenario_name: item.catalog_item_name ?? item.catalog_item_external_id,
    scenario_description: item.catalog_item_description ?? '',
    scenario_category: item.catalog_item_category ?? undefined,
    scenario_main_focus: item.catalog_item_main_focus ?? undefined,
    scenario_severity: item.catalog_item_severity,
    scenario_tags: [],
  } as ScenarioFormInput;

  return (
    <Drawer open handleClose={onClose} title={item.catalog_item_name ?? item.catalog_item_external_id}>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
      }}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 8,
        }}
        >
          {(item.catalog_item_platforms ?? []).map(platform => <Chip key={platform} label={platform} />)}
          <Text variant="content-caption">
            {[
              `v${item.catalog_item_version}`,
              item.catalog_item_injects_count != null ? `${item.catalog_item_injects_count} ${t('injects')}` : null,
              item.catalog_item_published_at ? `${t('Published')} ${nsd(item.catalog_item_published_at)}` : null,
              item.catalog_item_author ? `${t('by')} ${item.catalog_item_author}` : null,
            ].filter(Boolean).join(' · ')}
          </Text>
        </div>
        <ScenarioForm
          key={`${item.catalog_item_external_id}:${item.catalog_item_version}`}
          readOnly
          initialValues={initialValues}
          onSubmit={() => onInstall(item)}
          handleClose={onClose}
          extra={(
            <>
              {missing.length > 0 && (
                <Section title={t('Missing to run it')}>
                  {missing.map(prerequisite => (
                    <div
                      key={`${prerequisite.requirement_kind}:${prerequisite.requirement_key}`}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                    >
                      <Text variant="content-base" style={{ flex: 1 }}>
                        {prerequisite.requirement_catalog_connector_title ?? prerequisite.requirement_label}
                      </Text>
                      {prerequisite.requirement_status && (
                        <Chip label={t(prerequisite.requirement_status === 'NOT_RUNNING' ? 'Not running' : 'Not deployed')} severity="high" />
                      )}
                      {prerequisite.requirement_kind === 'INJECTOR' && prerequisite.requirement_catalog_connector_id && (
                        <Button asChild size="sm" priority="secondary">
                          <Link to={`/admin/integrations/catalog/${prerequisite.requirement_catalog_connector_id}`} target="_blank">
                            {t('Enable integration')}
                          </Link>
                        </Button>
                      )}
                    </div>
                  ))}
                </Section>
              )}
              {payloads.length > 0 && (
                <Section title={t('Payloads')}>
                  {payloads.map(payload => (
                    <Text key={`${payload.external_id}:${payload.version}`} variant="content-base">
                      {`${payload.name ?? payload.external_id} · v${payload.version ?? '-'}`}
                    </Text>
                  ))}
                </Section>
              )}
              {item.catalog_item_changelog && (
                <Section title={t('What\'s new in v{version}', { version: item.catalog_item_version })}>
                  <Text variant="content-base">{item.catalog_item_changelog}</Text>
                </Section>
              )}
            </>
          )}
          actions={(
            <>
              <Button type="button" priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
              {installed
                ? (
                    <Button asChild>
                      <Link to={`/admin/scenarios/${installed.scenario_id}`}>{t('Open')}</Link>
                    </Button>
                  )
                : <Button type="button" onClick={() => onInstall(item)} disabled={!canInstall}>{t('Install')}</Button>}
            </>
          )}
        />
      </div>
    </Drawer>
  );
};

export default ScenarioCatalogDrawer;
