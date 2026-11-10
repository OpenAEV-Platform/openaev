import {
  Alert,
  Button,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Input,
  SearchField,
  Text,
} from '@filigran/design-system';
import { AddOutlined, FactCheckOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import { type UserHelper } from '../../../actions/helper';
import FdsAccordion from '../../../components/common/accordion/FdsAccordion';
import { buildStatusRows } from '../../../components/common/facets/ContractFacets';
import { type FacetRow, type FacetSection, FacetSidebar } from '../../../components/common/facets/FacetFilters';
import MarketplaceBrowser from '../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceGrid from '../../../components/common/marketplace/MarketplaceGrid';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import { type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import { tint } from '../../../utils/tint';
import { DOMAIN_ICON_MAP } from '../common/domains/DomainsIcons';
import { approvalStatusLabel, NEW_ACTION_HINT, NEW_VERSION_HINT } from './approval/approvalStatusUtils';
import {
  defaultFilters,
  facetCounts,
  type FacetKey,
  facetValues,
  inScope,
  matches,
  type ReviewContext,
  type ReviewFilters,
  type ReviewMode,
  sameFilters,
  useReviewViews,
} from './approval/reviewViews';
import { isFromCollector, type QueueItem, useApprovalQueue, useQueueDrawer } from './approval/useApprovalQueue';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';
import computeActionFlags from './threatArsenalActionFlags';
import ThreatArsenalCard from './ThreatArsenalCard';
import { THREAT_ARSENAL_MY_CHANGES_PATH, THREAT_ARSENAL_TO_REVIEW_PATH } from './threatArsenalDeepLink';
import { actionDomains } from './threatArsenalDomainSections';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import ThreatArsenalPageHeader from './ThreatArsenalPageHeader';
import { NEW_VIEW_PARAM, VIEW_PARAM } from './ThreatArsenalTabs';

interface Props { mode: ReviewMode }

const PLATFORMS = ['Windows', 'Linux', 'MacOS'];

/**
 * The review tab of the Threat Arsenal (To review for approvers, My changes for authors) and the
 * custom views saved from it. Same page as the Active tab: the Hero and the tabs, the side filters on
 * the left (the Active tab's, plus new action or new version, source and who submitted it), the search
 * above the same cards grouped into New actions and New versions. A change to the filters can be kept
 * as a custom view; an open custom view saves its changes, and can be renamed or deleted. In My
 * changes the New actions group ends with "+ New action". Opening a card shows its Versions tab, where
 * the version is decided.
 */
const ThreatArsenalReviewPage: FunctionComponent<Props> = ({ mode }) => {
  const { t, tPick } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const me = useHelper((helper: UserHelper) => helper.getMe())?.user_id as string | undefined;
  const [searchParams, setSearchParams] = useSearchParams();

  const waiting = useApprovalQueue(['PENDING', 'REJECTED']);
  // Approved actions are many: the newest only. Platform content (Filigran, starter pack) and an
  // approver's own edits are approved automatically: they were never reviewed, so they are left out.
  const approved = useApprovalQueue(['APPROVED'], item => item.full?.action_approval_latest?.approval_automatic === false, {
    newestFirst: true,
    size: 20,
  });
  const drawer = useQueueDrawer();
  const allDomains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());

  const views = useReviewViews(mode, me);
  const openView = views.byId(searchParams.get(VIEW_PARAM) ?? '');
  const baseFilters = openView?.filters ?? defaultFilters();
  const [filters, setFilters] = useState<ReviewFilters>(baseFilters);
  // Another tab or view opened: start from its saved filters.
  useEffect(() => {
    setFilters(openView?.filters ?? defaultFilters());
  }, [openView?.id]);
  const changed = !sameFilters(filters, baseFilters);

  const [naming, setNaming] = useState<'new' | null>(null);
  const [viewName, setViewName] = useState('');
  // "New custom view" from the tab bar lands here with the dialog open.
  useEffect(() => {
    if (searchParams.get(NEW_VIEW_PARAM)) {
      setViewName('');
      setNaming('new');
      searchParams.delete(NEW_VIEW_PARAM);
      setSearchParams(searchParams, { replace: true });
    }
  }, [searchParams]);

  const context: ReviewContext = {
    me,
    // Each version is decided on its own: the status of the version listed.
    status: item => item.full?.action_approval_status ?? item.action.action_payload?.payload_approval_status ?? undefined,
    fromCollector: isFromCollector,
    // A new version only when an earlier version was approved; a superseded save does not count.
    isLaterVersion: item => item.laterVersion ?? (item.action.action_payload?.payload_installed_versions ?? 0) > 1,
    author: item => item.full?.action_last_modified_by_name ?? t('Unknown'),
    domain: item => actionDomains(item.action as ThreatArsenalAction, allDomains)[0]?.domain_name ?? 'Unknown',
  };
  const loading = waiting.items === null || approved.items === null;
  const name = (item: QueueItem) => tPick(item.action.action_labels);
  const all: QueueItem[] = useMemo(
    () => [...(waiting.items ?? []), ...(approved.items ?? [])].filter(item => inScope(item, mode, context)),
    [waiting.items, approved.items, mode, me],
  );
  const shown = all.filter(item => matches(item, filters, context, name(item)));

  const setSearch = (search: string) => setFilters({
    ...filters,
    search,
  });
  const toggle = (key: FacetKey, value: string) => {
    const ticked = filters.facets[key] ?? [];
    setFilters({
      ...filters,
      facets: {
        ...filters.facets,
        [key]: ticked.includes(value) ? ticked.filter(v => v !== value) : [...ticked, value],
      },
    });
  };
  const rows = (key: FacetKey, values: {
    value: string;
    label: string;
    icon?: () => ReactElement;
    hint?: string;
  }[]): FacetRow[] => {
    const counts = facetCounts(all, key, filters, context, name);
    return values.map(row => ({
      ...row,
      count: loading ? undefined : counts[row.value] ?? 0,
      checked: (filters.facets[key] ?? []).includes(row.value),
      onToggle: () => toggle(key, row.value),
    }));
  };
  const present = (key: FacetKey) => [...new Set(all.flatMap(item => facetValues(item, key, context)))].sort();

  // The Active tab's side filters, in the same order, then what only a review needs.
  const filterSections: FacetSection[] = [
    {
      id: 'domains',
      label: t('Domains'),
      rows: rows('domain', present('domain').map(domain => ({
        value: domain,
        label: t(domain),
        icon: DOMAIN_ICON_MAP[domain],
      }))),
    },
    {
      id: 'platforms',
      label: t('Platform'),
      rows: rows('platform', PLATFORMS.map(platform => ({
        value: platform,
        label: t(platform),
        icon: () => <PlatformIcon platform={platform} width={16} />,
      }))),
    },
    {
      id: 'status',
      label: t('Status'),
      rows: buildStatusRows({
        t,
        statusValues: filters.facets.status ?? [],
        statusCounts: loading ? undefined : facetCounts(all, 'status', filters, context, name),
        toggle: value => toggle('status', value),
      }),
    },
    {
      id: 'approval',
      label: t('Approval'),
      rows: rows('approval', (['PENDING', 'REJECTED', 'APPROVED'] as const).map(status => ({
        value: status,
        label: t(approvalStatusLabel(status)),
        hint: status === 'APPROVED' ? t('Approved after a review, newest first.') : undefined,
      }))),
    },
    {
      id: 'kind',
      label: t('Actions and versions'),
      rows: rows('kind', [
        {
          value: 'new',
          label: t('New actions'),
          hint: t(NEW_ACTION_HINT),
        },
        {
          value: 'version',
          label: t('New versions'),
          hint: t(NEW_VERSION_HINT),
        },
      ]),
    },
    {
      id: 'source',
      label: t('Source'),
      rows: rows('source', [
        {
          value: 'people',
          label: t('From people'),
        },
        {
          value: 'collectors',
          label: t('From collectors'),
        },
      ]),
    },
    // Authors only see their own changes: who submitted them says nothing there.
    ...(mode === 'to-review'
      ? [{
          id: 'author',
          label: t('Submitted by'),
          rows: rows('author', present('author').map(author => ({
            value: author,
            label: author,
          }))),
        }]
      : []),
  ].filter(section => section.rows.length > 0);

  const reviewPath = mode === 'to-review' ? THREAT_ARSENAL_TO_REVIEW_PATH : THREAT_ARSENAL_MY_CHANGES_PATH;
  const openNaming = () => {
    setViewName('');
    setNaming('new');
  };
  const submitName = () => {
    // A new view keeps what is on screen: the search and the ticked filters.
    const view = views.add(viewName.trim(), filters);
    navigate(`${reviewPath}?${VIEW_PARAM}=${encodeURIComponent(view.id)}`);
    setNaming(null);
  };
  const saveChanges = () => {
    if (openView) {
      views.update(openView.id, { filters });
    } else {
      openNaming();
    }
  };

  const canAdd = mode === 'my-changes';
  const sections = [
    {
      id: 'new',
      label: t('New actions'),
      hint: t('Created for the first time: nothing can use them until they are approved.'),
      items: shown.filter(item => !context.isLaterVersion(item)),
    },
    {
      id: 'version',
      label: t('New versions'),
      hint: t('Changes to actions already in use: the approved version keeps running until a decision.'),
      items: shown.filter(item => context.isLaterVersion(item)),
    },
  ].filter(section => section.items.length > 0 || (canAdd && section.id === 'new'));

  const card = (item: QueueItem) => (
    <ThreatArsenalCard
      key={item.action.injector_contract_id}
      action={item.action}
      selected={drawer.selected?.injector_contract_id === item.action.injector_contract_id}
      onSelect={() => drawer.open(item)}
      onUpdate={() => waiting.reload()}
      onDuplicate={() => waiting.reload()}
      onDelete={() => waiting.remove(item.action.injector_contract_id)}
      onRunTest={() => undefined}
      showRunTest={false}
      review={{ submittedBy: item.full?.action_last_modified_by_name ?? undefined }}
      primaryAction={{
        // Only a pending version has something to decide; a decided one is only looked at.
        label: mode === 'to-review' && context.status(item) === 'PENDING' ? t('Review') : t('View'),
        onClick: () => drawer.open(item),
      }}
      {...computeActionFlags(item.action as ThreatArsenalAction)}
    />
  );

  // GitHub's "+ Add item" at the end of a group: a card-sized tile that opens the Create form.
  const addTile = (
    <CreateThreatArsenalAction
      key="add"
      onCreate={() => waiting.reload()}
      renderTrigger={open => (
        <button
          type="button"
          onClick={open}
          style={{
            all: 'unset',
            boxSizing: 'border-box',
            cursor: 'pointer',
            minHeight: 280,
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: theme.spacing(1),
            padding: theme.spacing(3),
            textAlign: 'center',
            borderRadius: theme.shape.borderRadius,
            border: `1px dashed ${tint(theme.palette.text.primary, 24)}`,
            color: theme.palette.primary.main,
          }}
        >
          <AddOutlined />
          <Text variant="content-base-bold" style={{ color: 'inherit' }}>{t('New action')}</Text>
          <Text variant="content-compact" className="text-default-secondary">
            {t('It is sent for review when you create it.')}
          </Text>
        </button>
      )}
    />
  );

  const emptyState = (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: theme.spacing(1),
      padding: theme.spacing(4),
      textAlign: 'center',
    }}
    >
      <FactCheckOutlined />
      <Text as="h2" variant="title-xs" style={{ margin: 0 }}>{t('Nothing here')}</Text>
      <Text variant="content-compact" className="text-default-secondary">
        {mode === 'to-review'
          ? t('New actions and new versions waiting for approval show up here.')
          : t('What you submit for approval shows up here until it is decided.')}
      </Text>
    </div>
  );

  // Unsaved changes to the filters: said under the tabs, beside what they change (NN/g H1, Law of
  // Proximity), with the ways out in order of weight: keep them, keep them apart, or drop them (H3).
  const tabName = mode === 'to-review' ? t('To review') : t('My changes');
  const unsavedNotice = changed && (
    // FDS-WORKAROUND: Alert title/description are <p> with browser margins, which push the title below
    // its icon — remove when the library resets them — see DS Alert
    <div className="review-unsaved-alert">
      <style>{'.review-unsaved-alert p { margin: 0; }'}</style>
      <Alert
        severity="info"
        title={t('You changed the filters of {name}', { name: openView?.label ?? tabName })}
        description={openView
          ? t('Save them in this view, or keep this view as it is and save them as a new one.')
          : t('Save them as a custom view to open them again in one click.')}
        action={(
          <div style={{
            display: 'flex',
            gap: theme.spacing(1),
            flexWrap: 'wrap',
          }}
          >
            <Button size="sm" priority="tertiary" onClick={() => setFilters(baseFilters)}>{t('Discard')}</Button>
            {openView && <Button size="sm" priority="tertiary" onClick={openNaming}>{t('Save as new view')}</Button>}
            <Button size="sm" priority="secondary" onClick={saveChanges}>{openView ? t('Save changes') : t('Save as custom view')}</Button>
          </div>
        )}
      />
    </div>
  );

  const toolbar = (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: theme.spacing(1),
      flexWrap: 'wrap',
    }}
    >
      <div style={{
        flex: 1,
        minWidth: 200,
      }}
      >
        <SearchField
          aria-label={t('Search these results...')}
          placeholder={t('Search these results...')}
          fullWidth
          value={filters.search}
          onChange={event => setSearch(event.target.value)}
          onClear={() => setSearch('')}
        />
      </div>
    </div>
  );

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      paddingBottom: theme.spacing(4),
    }}
    >
      {/* No breadcrumb: To review and My changes are tabs of the Threat Arsenal, like Active and Available. */}
      <MarketplaceBrowser
        hero={<ThreatArsenalPageHeader active={mode} onCreate={() => waiting.reload()} />}
        tabs={unsavedNotice || undefined}
        sidebar={<FacetSidebar title={t('Filters')} sections={filterSections} />}
        toolbar={toolbar}
      >
        {loading || sections.length === 0
          ? <MarketplaceGrid loading={loading} isEmpty={sections.length === 0} emptyState={emptyState}>{null}</MarketplaceGrid>
          : sections.map(section => (
              // The DS accordion, as in the Versions and History tabs: the group and its count, open by default.
              <FdsAccordion
                key={section.id}
                defaultExpanded
                title={section.label}
                meta={<Text variant="content-compact" className="text-default-secondary">{String(section.items.length)}</Text>}
              >
                <Text
                  variant="content-compact"
                  className="text-default-secondary"
                  style={{
                    display: 'block',
                    marginBottom: 12,
                  }}
                >
                  {section.hint}
                </Text>
                <MarketplaceGrid loading={false} isEmpty={false} emptyState={null}>
                  {section.items.map(card)}
                  {canAdd && section.id === 'new' && addTile}
                </MarketplaceGrid>
              </FdsAccordion>
            ))}
      </MarketplaceBrowser>

      <Dialog open={naming !== null} onOpenChange={open => !open && setNaming(null)}>
        <DialogContent size="sm">
          <DialogTitle>{t('New custom view')}</DialogTitle>
          <DialogBody>
            <Text variant="content-compact" className="text-default-secondary">
              {t('Keeps the current search and filters as a view of your own. It opens from Custom views, or as a tab. Only you see it.')}
            </Text>
            <Input
              label={t('Name')}
              value={viewName}
              onChange={event => setViewName(event.target.value)}
              maxLength={60}
              autoFocus
            />
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={() => setNaming(null)}>{t('Cancel')}</Button>
            <Button disabled={viewName.trim() === ''} onClick={submitName}>{t('Save')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {drawer.selected && (
        <ThreatArsenalInformationDrawer
          open
          initialTab="versions"
          focusMigration={searchParams.get('focus') === 'migrate'}
          onClose={drawer.close}
          threatArsenalAction={drawer.selected}
          onApprovalChanged={() => waiting.reload()}
        />
      )}
    </div>
  );
};

export default ThreatArsenalReviewPage;
