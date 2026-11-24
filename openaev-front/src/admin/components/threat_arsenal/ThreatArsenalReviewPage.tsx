import { Chip, SearchField, Text } from '@filigran/design-system';
import { AddOutlined, FactCheckOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import { type UserHelper } from '../../../actions/helper';
import { fetchMyDrafts } from '../../../actions/threat_arsenals/threatArsenal-draft-actions';
import FdsAccordion from '../../../components/common/accordion/FdsAccordion';
import Drawer from '../../../components/common/Drawer';
import { buildStatusRows } from '../../../components/common/facets/ContractFacets';
import { type FacetRow, type FacetSection, FacetSidebar } from '../../../components/common/facets/FacetFilters';
import MarketplaceBrowser from '../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceGrid from '../../../components/common/marketplace/MarketplaceGrid';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import { type Domain, type PayloadDraftOutput, type ThreatArsenalAction } from '../../../utils/api-types';
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
} from './approval/reviewViews';
import { isFromCollector, type QueueItem, useApprovalQueue, useQueueDrawer } from './approval/useApprovalQueue';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';
import ThreatArsenalDraftEditor from './drafts/ThreatArsenalDraftEditor';
import computeActionFlags from './threatArsenalActionFlags';
import ThreatArsenalCard from './ThreatArsenalCard';
import { actionDomains } from './threatArsenalDomainSections';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import ThreatArsenalPageHeader from './ThreatArsenalPageHeader';

interface Props { mode: ReviewMode }

const PLATFORMS = ['Windows', 'Linux', 'MacOS'];

/**
 * The review tab of the Threat Arsenal (To review for approvers, My changes for authors) and the
 * custom views saved from it. Same page as the Active tab: the Hero and the tabs, the side filters on
 * the left (the Active tab's, plus new action or new version, source and who submitted it), the search
 * above the same cards grouped into New actions and New versions. A built-in tab: its filters can be
 * changed while looking, never saved (custom views are saved filters of the Active tab). In My
 * changes the New actions group ends with "+ New action". Opening a card shows its Versions tab, where
 * the version is decided.
 */
const ThreatArsenalReviewPage: FunctionComponent<Props> = ({ mode }) => {
  const { t, tPick, nsdt } = useFormatter();
  const theme = useTheme();
  const me = useHelper((helper: UserHelper) => helper.getMe())?.user_id as string | undefined;
  const [searchParams] = useSearchParams();

  const waiting = useApprovalQueue(['PENDING', 'REJECTED']);
  // Approved actions are many: the newest only. Platform content (Filigran, starter pack) and an
  // approver's own edits are approved automatically: they were never reviewed, so they are left out.
  const approved = useApprovalQueue(['APPROVED'], item => item.full?.action_approval_latest?.approval_automatic === false, {
    newestFirst: true,
    size: 20,
  });
  const drawer = useQueueDrawer();
  const allDomains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());

  const [filters, setFilters] = useState<ReviewFilters>(defaultFilters);

  // Payload drafts (My changes): what I saved and did not publish yet, new actions included.
  const [myDrafts, setMyDrafts] = useState<PayloadDraftOutput[]>([]);
  const [editingDraft, setEditingDraft] = useState<PayloadDraftOutput | null>(null);
  const loadDrafts = () => fetchMyDrafts()
    .then((response: { data: PayloadDraftOutput[] }) => setMyDrafts(response.data))
    .catch(() => setMyDrafts([]));
  useEffect(() => {
    if (mode === 'my-changes') loadDrafts();
  }, [mode]);
  const draftName = (draft: PayloadDraftOutput) => (draft.draft_content as { action_name?: string } | undefined)?.action_name || t('Untitled');
  const draftKind = (draft: PayloadDraftOutput) => {
    if (draft.draft_new_action) return t('New action');
    if (draft.draft_rework) return t('v{version} · Rejected, being fixed', { version: draft.draft_source_version ?? '' });
    return t('Draft from v{version}', { version: draft.draft_source_version ?? '' });
  };

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
  const shownDrafts = mode === 'my-changes'
    ? myDrafts.filter(draft => !filters.search || draftName(draft).toLowerCase().includes(filters.search.toLowerCase()))
    : [];

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

  const draftTile = (draft: PayloadDraftOutput, onClick: () => void) => (
    <button
      type="button"
      onClick={onClick}
      style={{
        all: 'unset',
        boxSizing: 'border-box',
        cursor: 'pointer',
        minHeight: 160,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'flex-start',
        gap: theme.spacing(1),
        padding: theme.spacing(2),
        borderRadius: theme.shape.borderRadius,
        border: `1px solid ${tint(theme.palette.text.primary, 16)}`,
      }}
    >
      <Text variant="content-base-bold">{draftName(draft)}</Text>
      <div style={{
        display: 'flex',
        gap: 4,
        flexWrap: 'wrap',
      }}
      >
        <Chip label={t('Draft')} />
        <Chip label={draftKind(draft)} />
      </div>
      <Text variant="content-compact" className="text-default-secondary">
        {t('Saved {date}', { date: nsdt(draft.draft_updated_at) })}
      </Text>
    </button>
  );
  const draftCard = (draft: PayloadDraftOutput) => (draft.draft_new_action
    ? (
        <CreateThreatArsenalAction
          key={draft.draft_id}
          draft={draft}
          onCreate={() => waiting.reload()}
          onDraftsChange={loadDrafts}
          renderTrigger={open => draftTile(draft, open)}
        />
      )
    : <div key={draft.draft_id}>{draftTile(draft, () => setEditingDraft(draft))}</div>);

  // GitHub's "+ Add item" at the end of a group: a card-sized tile that opens the Create form.
  const addTile = (
    <CreateThreatArsenalAction
      key="add"
      onCreate={() => waiting.reload()}
      onDraftsChange={loadDrafts}
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
        sidebar={<FacetSidebar title={t('Filters')} sections={filterSections} />}
        toolbar={toolbar}
      >
        {!loading && shownDrafts.length > 0 && (
          <FdsAccordion
            defaultExpanded
            title={t('Drafts')}
            meta={<Text variant="content-compact" className="text-default-secondary">{String(shownDrafts.length)}</Text>}
          >
            <Text
              variant="content-compact"
              className="text-default-secondary"
              style={{
                display: 'block',
                marginBottom: 12,
              }}
            >
              {t('Saved, not published: nothing uses them and nobody reviews them yet.')}
            </Text>
            <MarketplaceGrid loading={false} isEmpty={false} emptyState={null}>
              {shownDrafts.map(draftCard)}
            </MarketplaceGrid>
          </FdsAccordion>
        )}
        {loading || sections.length === 0
          ? <MarketplaceGrid loading={loading} isEmpty={sections.length === 0 && shownDrafts.length === 0} emptyState={emptyState}>{null}</MarketplaceGrid>
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

      <Drawer open={editingDraft !== null} handleClose={() => setEditingDraft(null)} title={t('Edit draft')}>
        {editingDraft?.draft_action_id
          ? (
              <ThreatArsenalDraftEditor
                actionId={editingDraft.draft_action_id}
                initialSelection={`draft:${editingDraft.draft_id}`}
                onClose={() => {
                  setEditingDraft(null);
                  loadDrafts();
                }}
                onPublished={() => {
                  setEditingDraft(null);
                  loadDrafts();
                  waiting.reload();
                }}
              />
            )
          : <></>}
      </Drawer>
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
