import {
  Button,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Input,
  SearchField,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Text,
} from '@filigran/design-system';
import { BookmarkAddOutlined, ExpandLess, ExpandMore, FactCheckOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';

import { type UserHelper } from '../../../actions/helper';
import Breadcrumbs from '../../../components/Breadcrumbs';
import MarketplaceBrowser from '../../../components/common/marketplace/MarketplaceBrowser';
import MarketplaceGrid from '../../../components/common/marketplace/MarketplaceGrid';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type ThreatArsenalAction } from '../../../utils/api-types';
import { displayedApprovalStatus } from './approval/approvalStatusUtils';
import {
  builtInViews,
  matches,
  readSavedViews,
  type ReviewContext,
  type ReviewFilters,
  type ReviewKind,
  type ReviewSource,
  type ReviewView,
  writeSavedViews,
} from './approval/reviewViews';
import ReviewViewsSidebar from './approval/ReviewViewsSidebar';
import { isFromCollector, type QueueItem, useApprovalQueue, useQueueDrawer } from './approval/useApprovalQueue';
import computeActionFlags from './threatArsenalActionFlags';
import ThreatArsenalCard from './ThreatArsenalCard';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import ThreatArsenalPageHeader from './ThreatArsenalPageHeader';

interface Props { mode: 'to-review' | 'my-changes' }

const VIEW_PARAM = 'view';

/**
 * The review pages of the Threat Arsenal (To review for approvers, My changes for authors), after
 * GitHub's pull request and issue dashboards: built-in views and saved views on the left, a search
 * and two filters on top, and the same cards as the Active tab, grouped into New actions and New
 * versions that fold like GitHub's inbox sections. Opening a card shows its Versions tab, where the
 * version is decided.
 */
const ThreatArsenalReviewPage: FunctionComponent<Props> = ({ mode }) => {
  const { t, tPick } = useFormatter();
  const theme = useTheme();
  const me = useHelper((helper: UserHelper) => helper.getMe())?.user_id as string | undefined;
  const [searchParams, setSearchParams] = useSearchParams();

  const waiting = useApprovalQueue(['PENDING', 'REJECTED']);
  // Approved actions are many: the newest only, without their detail.
  const approved = useApprovalQueue(['APPROVED'], () => true, {
    details: false,
    newestFirst: true,
    size: 20,
  });
  const drawer = useQueueDrawer();

  const builtIn = builtInViews(mode);
  const [saved, setSaved] = useState<ReviewView[]>(() => readSavedViews(mode, me));
  const views = [...builtIn, ...saved];
  const activeView = views.find(view => view.id === searchParams.get(VIEW_PARAM)) ?? builtIn[0];
  const [filters, setFilters] = useState<ReviewFilters>(activeView.filters);
  const [saving, setSaving] = useState(false);
  const [viewName, setViewName] = useState('');
  const [folded, setFolded] = useState<Record<string, boolean>>({});

  const context: ReviewContext = {
    me,
    status: item => displayedApprovalStatus(
      item.full?.action_approval_status ?? item.action.action_payload?.payload_approval_status,
      !!item.full?.action_pending_version,
    ) ?? undefined,
    fromCollector: isFromCollector,
    isLaterVersion: item => !!item.full?.action_pending_version || (item.action.action_payload?.payload_installed_versions ?? 0) > 1,
  };
  const all: QueueItem[] = useMemo(() => [...(waiting.items ?? []), ...(approved.items ?? [])], [waiting.items, approved.items]);
  const loading = waiting.items === null || approved.items === null;
  const listed = (view: ReviewView, applied: ReviewFilters) => all.filter(item => matches(item, view, applied, context, tPick(item.action.action_labels)));
  const counts = Object.fromEntries(views.map(view => [view.id, listed(view, view.filters).length]));
  const shown = listed(activeView, filters);

  const selectView = (view: ReviewView) => {
    setFilters(view.filters);
    searchParams.set(VIEW_PARAM, view.id);
    setSearchParams(searchParams, { replace: true });
  };
  const saveView = () => {
    const view: ReviewView = {
      id: `saved-${Date.now()}`,
      label: viewName.trim(),
      base: activeView.base,
      filters,
      saved: true,
    };
    const next = [...saved, view];
    setSaved(next);
    writeSavedViews(mode, me, next);
    setSaving(false);
    setViewName('');
    selectView(view);
  };
  const deleteView = (view: ReviewView) => {
    const next = saved.filter(other => other.id !== view.id);
    setSaved(next);
    writeSavedViews(mode, me, next);
    if (view.id === activeView.id) selectView(builtIn[0]);
  };

  const sections = [
    {
      id: 'new',
      label: t('New actions'),
      items: shown.filter(item => !context.isLaterVersion(item)),
    },
    {
      id: 'version',
      label: t('New versions'),
      items: shown.filter(item => context.isLaterVersion(item)),
    },
  ].filter(section => section.items.length > 0);

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
        label: mode === 'to-review' ? t('Review') : t('View'),
        onClick: () => drawer.open(item),
      }}
      {...computeActionFlags(item.action as ThreatArsenalAction)}
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
          onChange={event => setFilters({
            ...filters,
            search: event.target.value,
          })}
          onClear={() => setFilters({
            ...filters,
            search: '',
          })}
        />
      </div>
      <Select
        value={filters.source}
        onValueChange={value => setFilters({
          ...filters,
          source: value as ReviewSource,
        })}
      >
        <SelectTrigger aria-label={t('Source')} style={{ minWidth: 150 }}><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem value="all">{t('All sources')}</SelectItem>
          <SelectItem value="people">{t('From people')}</SelectItem>
          <SelectItem value="collectors">{t('From collectors')}</SelectItem>
        </SelectContent>
      </Select>
      <Select
        value={filters.kind}
        onValueChange={value => setFilters({
          ...filters,
          kind: value as ReviewKind,
        })}
      >
        <SelectTrigger aria-label={t('Type')} style={{ minWidth: 150 }}><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem value="all">{t('Actions and versions')}</SelectItem>
          <SelectItem value="new">{t('New actions')}</SelectItem>
          <SelectItem value="version">{t('New versions')}</SelectItem>
        </SelectContent>
      </Select>
      <Button priority="secondary" startIcon={<BookmarkAddOutlined fontSize="small" />} onClick={() => setSaving(true)}>
        {t('Save view')}
      </Button>
    </div>
  );

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      paddingBottom: theme.spacing(4),
    }}
    >
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Threat Arsenal'),
          link: '/admin/threat-arsenal',
        }, {
          label: t(mode === 'to-review' ? 'To review' : 'My changes'),
          current: true,
        }]}
      />
      <MarketplaceBrowser
        hero={<ThreatArsenalPageHeader active={mode} />}
        sidebar={(
          <ReviewViewsSidebar
            builtIn={builtIn}
            saved={saved}
            activeId={activeView.id}
            counts={counts}
            onSelect={selectView}
            onAdd={() => setSaving(true)}
            onDelete={deleteView}
          />
        )}
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
                {!folded[section.id] && (
                  <MarketplaceGrid loading={false} isEmpty={false} emptyState={null}>
                    {section.items.map(card)}
                  </MarketplaceGrid>
                )}
              </section>
            ))}
      </MarketplaceBrowser>

      <Dialog open={saving} onOpenChange={open => !open && setSaving(false)}>
        <DialogContent size="sm">
          <DialogTitle>{t('Save view')}</DialogTitle>
          <DialogBody>
            <Text variant="content-compact" className="text-default-secondary">
              {t('Saves "{view}" with the current search and filters, so you can open it again in one click.', { view: activeView.saved ? activeView.label : t(activeView.label) })}
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
            <Button priority="secondary" onClick={() => setSaving(false)}>{t('Cancel')}</Button>
            <Button disabled={viewName.trim() === ''} onClick={saveView}>{t('Save')}</Button>
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
