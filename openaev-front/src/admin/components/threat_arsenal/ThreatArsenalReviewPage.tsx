import {
  Button,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Input,
  MenuItem,
  MenuSeparator,
  MenuSub,
  MenuSubContent,
  MenuSubTrigger,
  SearchField,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Tabs,
  TabsList,
  TabsMenuTrigger,
  TabsTrigger,
  Text,
} from '@filigran/design-system';
import { AddOutlined, DeleteOutlined, EditOutlined, ExpandLess, ExpandMore, FactCheckOutlined, OpenInNewOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import { type UserHelper } from '../../../actions/helper';
import MarketplaceBrowser from '../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceGrid from '../../../components/common/marketplace/MarketplaceGrid';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import { tint } from '../../../utils/tint';
import { displayedApprovalStatus } from './approval/approvalStatusUtils';
import {
  builtInViews,
  matches,
  matchesFilters,
  readSavedViews,
  type ReviewContext,
  type ReviewFilters,
  type ReviewKind,
  type ReviewSource,
  type ReviewView,
  sameFilters,
  sliceValueOf,
  writeSavedViews,
} from './approval/reviewViews';
import { isFromCollector, type QueueItem, useApprovalQueue, useQueueDrawer } from './approval/useApprovalQueue';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';
import computeActionFlags from './threatArsenalActionFlags';
import ThreatArsenalCard from './ThreatArsenalCard';
import { actionDomains } from './threatArsenalDomainSections';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import ThreatArsenalPageHeader from './ThreatArsenalPageHeader';

interface Props { mode: 'to-review' | 'my-changes' }

const VIEW_PARAM = 'view';

/**
 * The review pages of the Threat Arsenal (To review for approvers, My changes for authors), after
 * GitHub Projects: views as tabs with their counts (built-in ones per role, plus the person's own
 * with "+ New view"), a filter bar that offers to save or discard what changed, a "Slice by" rail,
 * and the same cards as the Active tab grouped into New actions and New versions. In My changes the
 * New actions group ends with "+ New action", like GitHub's "+ Add item". Opening a card shows its
 * Versions tab, where the version is decided.
 */
const ThreatArsenalReviewPage: FunctionComponent<Props> = ({ mode }) => {
  const { t, tPick } = useFormatter();
  const theme = useTheme();
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

  const builtIn = builtInViews(mode);
  const [saved, setSaved] = useState<ReviewView[]>(() => readSavedViews(mode, me));
  const views = [...builtIn, ...saved];
  const activeView = views.find(view => view.id === searchParams.get(VIEW_PARAM)) ?? builtIn[0];
  const viewLabel = (view: ReviewView) => (view.saved ? view.label : t(view.label));
  // Built-in views are always tabs, in a fixed order (the workflow, the same for everyone); the person's
  // own views sit in one "Custom views" tab menu, so the tab row never grows.
  const [filters, setFilters] = useState<ReviewFilters>(activeView.filters);
  const [naming, setNaming] = useState<'new' | 'rename' | null>(null);
  const [viewName, setViewName] = useState('');
  const [folded, setFolded] = useState<Record<string, boolean>>({});
  const changed = !sameFilters(filters, activeView.filters);

  const context: ReviewContext = {
    me,
    status: item => displayedApprovalStatus(
      item.full?.action_approval_status ?? item.action.action_payload?.payload_approval_status,
      !!item.full?.action_pending_version,
    ) ?? undefined,
    fromCollector: isFromCollector,
    isLaterVersion: item => !!item.full?.action_pending_version || (item.action.action_payload?.payload_installed_versions ?? 0) > 1,
    author: item => item.full?.action_last_modified_by_name ?? t('Unknown'),
    domain: (item) => {
      const domain = actionDomains(item.action as ThreatArsenalAction, allDomains)[0];
      return domain ? t(domain.domain_name) : t('Unknown');
    },
  };
  const all: QueueItem[] = useMemo(() => [...(waiting.items ?? []), ...(approved.items ?? [])], [waiting.items, approved.items]);
  const loading = waiting.items === null || approved.items === null;
  const name = (item: QueueItem) => tPick(item.action.action_labels);
  const counts = Object.fromEntries(views.map(view => [view.id, all.filter(item => matches(item, view, view.filters, context, name(item))).length]));
  const shown = all.filter(item => matches(item, activeView, filters, context, name(item)));

  // "Submitted by" choices: who submitted what the view and the other filters list.
  const unsliced = all.filter(item => matchesFilters(item, activeView, filters, context, name(item)));
  const authors = [...new Set(unsliced.map(item => sliceValueOf(item, 'author', context)))].sort();
  const selectView = (view: ReviewView) => {
    setFilters(view.filters);
    searchParams.set(VIEW_PARAM, view.id);
    setSearchParams(searchParams, { replace: true });
  };
  const storeViews = (next: ReviewView[]) => {
    setSaved(next);
    writeSavedViews(mode, me, next);
  };
  const [renaming, setRenaming] = useState<ReviewView | null>(null);
  const openNaming = (kind: 'new' | 'rename', target?: ReviewView) => {
    setRenaming(kind === 'rename' ? target ?? activeView : null);
    setViewName(kind === 'rename' ? (target ?? activeView).label : '');
    setNaming(kind);
  };
  const submitName = () => {
    if (naming === 'rename') {
      const renamedId = renaming?.id ?? activeView.id;
      storeViews(saved.map(view => (view.id === renamedId
        ? {
            ...view,
            label: viewName.trim(),
          }
        : view)));
    } else {
      // A new view starts from what is on screen: the current view's base, search, filters and slice.
      const view: ReviewView = {
        id: `saved-${Date.now()}`,
        label: viewName.trim(),
        base: activeView.base,
        filters,
        saved: true,
      };
      storeViews([...saved, view]);
      selectView(view);
    }
    setNaming(null);
  };
  const saveChanges = () => {
    if (!activeView.saved) {
      openNaming('new');
      return;
    }
    storeViews(saved.map(view => (view.id === activeView.id
      ? {
          ...view,
          filters,
        }
      : view)));
  };
  const deleteView = (target: ReviewView) => {
    storeViews(saved.filter(view => view.id !== target.id));
    if (target.id === activeView.id) selectView(builtIn[0]);
  };
  const setFilter = (patch: Partial<ReviewFilters>) => setFilters({
    ...filters,
    ...patch,
  });

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

  const deleteStyle = { color: 'var(--color-feedback-error-primary)' };
  const viewTabs = (
    <Tabs value={activeView.id} onValueChange={id => selectView(views.find(view => view.id === id) ?? builtIn[0])} panels="external">
      <TabsList aria-label={t('Views')}>
        {builtIn.map(view => (
          <TabsTrigger
            key={view.id}
            value={view.id}
            badge={counts[view.id] ?? 0}
            badgeLabel={t('{count, plural, one {# item} other {# items}}', { count: String(counts[view.id] ?? 0) })}
          >
            {viewLabel(view)}
          </TabsTrigger>
        ))}
        {/* One menu for the person's own views: open, rename or delete each one on its own row. */}
        <TabsMenuTrigger
          active={!!activeView.saved}
          menu={(
            <>
              {saved.map(view => (
                <MenuSub key={view.id}>
                  <MenuSubTrigger>{`${viewLabel(view)} · ${counts[view.id] ?? 0}`}</MenuSubTrigger>
                  <MenuSubContent>
                    <MenuItem startIcon={<OpenInNewOutlined fontSize="small" />} onSelect={() => selectView(view)}>{t('Open view')}</MenuItem>
                    <MenuItem startIcon={<EditOutlined fontSize="small" />} onSelect={() => openNaming('rename', view)}>{t('Rename view')}</MenuItem>
                    <MenuItem startIcon={<DeleteOutlined fontSize="small" style={deleteStyle} />} style={deleteStyle} onSelect={() => deleteView(view)}>
                      {t('Delete view')}
                    </MenuItem>
                  </MenuSubContent>
                </MenuSub>
              ))}
              {saved.length > 0 && <MenuSeparator />}
              <MenuItem startIcon={<AddOutlined fontSize="small" />} onSelect={() => openNaming('new')}>{t('New custom view')}</MenuItem>
            </>
          )}
        >
          {activeView.saved ? viewLabel(activeView) : t('Custom views')}
        </TabsMenuTrigger>
      </TabsList>
    </Tabs>
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
          onChange={event => setFilter({ search: event.target.value })}
          onClear={() => setFilter({ search: '' })}
        />
      </div>
      <Select value={filters.source} onValueChange={value => setFilter({ source: value as ReviewSource })}>
        <SelectTrigger aria-label={t('Source')} style={{ minWidth: 150 }}><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem value="all">{t('All sources')}</SelectItem>
          <SelectItem value="people">{t('From people')}</SelectItem>
          <SelectItem value="collectors">{t('From collectors')}</SelectItem>
        </SelectContent>
      </Select>
      <Select value={filters.kind} onValueChange={value => setFilter({ kind: value as ReviewKind })}>
        <SelectTrigger aria-label={t('Type')} style={{ minWidth: 150 }}><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem value="all">{t('Actions and versions')}</SelectItem>
          <SelectItem value="new">{t('New actions')}</SelectItem>
          <SelectItem value="version">{t('New versions')}</SelectItem>
        </SelectContent>
      </Select>
      {mode === 'to-review' && (
        <Select
          value={filters.sliceField === 'author' && filters.sliceValue ? filters.sliceValue : 'all'}
          onValueChange={value => setFilter({
            sliceField: 'author',
            sliceValue: value === 'all' ? null : value,
          })}
        >
          <SelectTrigger aria-label={t('Submitted by')} style={{ minWidth: 170 }}><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('Anyone')}</SelectItem>
            {authors.map(author => <SelectItem key={author} value={author}>{author}</SelectItem>)}
          </SelectContent>
        </Select>
      )}
      {/* Built-in views are fixed lists: a filter change can be kept as a custom view; a custom view saves its changes. */}
      {changed && (
        <>
          <Button priority="tertiary" onClick={() => setFilters(activeView.filters)}>{t('Discard')}</Button>
          <Button priority="secondary" onClick={saveChanges}>{activeView.saved ? t('Save changes') : t('Save as custom view')}</Button>
        </>
      )}
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
        tabs={viewTabs}
        sidebar={null}
        toolbar={toolbar}
      >
        {loading || sections.length === 0
          ? <MarketplaceGrid loading={loading} isEmpty={sections.length === 0} emptyState={emptyState}>{null}</MarketplaceGrid>
          : sections.map(section => (
              <section
                key={section.id}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: theme.spacing(1.5),
                }}
              >
                <button
                  type="button"
                  aria-expanded={!folded[section.id]}
                  onClick={() => setFolded({
                    ...folded,
                    [section.id]: !folded[section.id],
                  })}
                  style={{
                    all: 'unset',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: theme.spacing(1),
                  }}
                >
                  {folded[section.id] ? <ExpandMore fontSize="small" /> : <ExpandLess fontSize="small" />}
                  <Text as="h2" variant="title-xs" style={{ margin: 0 }}>{section.label}</Text>
                  <Text variant="content-caption" className="text-default-secondary">{String(section.items.length)}</Text>
                </button>
                <Text variant="content-compact" className="text-default-secondary" style={{ marginTop: -8 }}>{section.hint}</Text>
                {!folded[section.id] && (
                  <MarketplaceGrid loading={false} isEmpty={false} emptyState={null}>
                    {section.items.map(card)}
                    {canAdd && section.id === 'new' && addTile}
                  </MarketplaceGrid>
                )}
              </section>
            ))}
      </MarketplaceBrowser>

      <Dialog open={naming !== null} onOpenChange={open => !open && setNaming(null)}>
        <DialogContent size="sm">
          <DialogTitle>{naming === 'rename' ? t('Rename view') : t('New custom view')}</DialogTitle>
          <DialogBody>
            {naming === 'new' && (
              <Text variant="content-compact" className="text-default-secondary">
                {t('Keeps "{view}" with the current search and filters, under Custom views. Only you see it.', { view: viewLabel(activeView) })}
              </Text>
            )}
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
