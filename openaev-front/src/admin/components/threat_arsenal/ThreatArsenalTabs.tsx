import {
  CustomizePanel,
  CustomizePanelContent,
  type CustomizePanelItem,
  CustomizePanelTrigger,
  IconButton,
  MenuItem,
  MenuSeparator,
  MenuSub,
  MenuSubContent,
  MenuSubTrigger,
  Tabs,
  TabsList,
  TabsMenuTrigger,
  TabsTrigger,
} from '@filigran/design-system';
import { AddOutlined, DeleteOutlined, EditOutlined, TuneOutlined } from '@mui/icons-material';
import { type FunctionComponent, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';

import { type UserHelper } from '../../../actions/helper';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SplitTab from '../../../components/common/tabs/SplitTab';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ReviewViewDialogs, { type ViewEdit } from './approval/ReviewViewDialogs';
import { MAX_VIEW_TABS, type ReviewMode, useReviewViews } from './approval/reviewViews';
import useApprovalDesignVariant from './approval/useApprovalDesignVariant';
import { THREAT_ARSENAL_MY_CHANGES_PATH, THREAT_ARSENAL_TO_REVIEW_PATH } from './threatArsenalDeepLink';
import useThreatArsenalFacetCounts from './useThreatArsenalFacetCounts';

export type ThreatArsenalTab = 'installed' | 'available' | 'to-review' | 'my-changes';

/** Opens a custom view on the review tab. */
export const VIEW_PARAM = 'view';
/** Opens the "New custom view" dialog on the review tab. */
export const NEW_VIEW_PARAM = 'new-view';

interface Props { active: ThreatArsenalTab }

/**
 * The one set of routed tabs of the Threat Arsenal. Active, Available and the review tab of the role
 * ("To review" for holders of "Approve content", "My changes" for people who edit without approving)
 * always come first, for everyone. Then up to three custom views the person chose to show as tabs,
 * then "Custom views", which opens any other view in one click or starts a new one. The design system
 * Customize Panel at the end chooses and orders the custom views shown as tabs. In the lighter
 * "filter" option there is no review tab, hence no custom views.
 */
const ThreatArsenalTabs: FunctionComponent<Props> = ({ active }) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const variant = useApprovalDesignVariant();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [edit, setEdit] = useState<ViewEdit | null>(null);
  const me = useHelper((helper: UserHelper) => helper.getMe())?.user_id as string | undefined;
  const canApprove = ability.can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS);
  const canSubmit = !canApprove && ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const reviewMode: ReviewMode | null = (() => {
    if (variant !== 'queue') return null;
    if (canApprove) return 'to-review';
    return canSubmit ? 'my-changes' : null;
  })();
  const reviewPath = reviewMode === 'to-review' ? THREAT_ARSENAL_TO_REVIEW_PATH : THREAT_ARSENAL_MY_CHANGES_PATH;
  const views = useReviewViews(reviewMode ?? 'to-review', me);

  // Counts over the whole arsenal, never the current page or filters.
  const allActions = useMemo(() => buildSearchPagination({}), []);
  const facetCounts = useThreatArsenalFacetCounts(allActions);
  const pendingCount = facetCounts?.approvals?.PENDING ?? 0;

  const openViewId = active === reviewMode ? searchParams.get(VIEW_PARAM) : null;
  const openView = openViewId ? views.byId(openViewId) : undefined;
  const value = openView ? `view:${openView.id}` : active;
  const viewPath = (id: string) => `${reviewPath}?${VIEW_PARAM}=${encodeURIComponent(id)}`;

  const tab = (tabValue: string, label: string, to: string) => (
    <TabsTrigger key={tabValue} value={tabValue} asChild>
      <Link to={to} aria-current={value === tabValue ? 'page' : undefined}>{label}</Link>
    </TabsTrigger>
  );

  const deleteStyle = { color: 'var(--color-feedback-error-primary)' };
  const openFromMenu = (id: string) => {
    setMenuOpen(false);
    navigate(viewPath(id));
  };

  const panelItems: CustomizePanelItem[] = views.layout.map(item => ({
    ...item,
    label: views.byId(item.id)?.label ?? item.id,
  }));
  const defaultPanelItems: CustomizePanelItem[] = views.views.map((view, index) => ({
    id: view.id,
    label: view.label,
    visible: true,
    pinned: index < MAX_VIEW_TABS,
  }));

  const customize = reviewMode && views.views.length > 0 && (
    <CustomizePanel
      mode="show"
      items={panelItems}
      defaultItems={defaultPanelItems}
      maxPinned={MAX_VIEW_TABS}
      onSave={next => views.arrange(next.map(({ id, visible, pinned }) => ({
        id,
        visible,
        pinned,
      })))}
    >
      <CustomizePanelTrigger asChild>
        <IconButton size="sm" priority="tertiary" icon={<TuneOutlined fontSize="small" />} aria-label={t('Customize tabs')} />
      </CustomizePanelTrigger>
      <CustomizePanelContent
        align="end"
        title={t('Custom views')}
        labels={{
          pinnedSection: t('Shown as tabs'),
          unpinnedSection: t('Under Custom views'),
          emptyPinned: t('No custom view shown as a tab'),
          emptyUnpinned: t('Every custom view is a tab'),
          counter: ({ pinned, maxPinned }) => t('{pinned} of {max} tabs. Active, Available and the review tab are always shown.', {
            pinned: String(pinned),
            max: String(maxPinned),
          }),
          limitReached: max => t('{max} custom views at most are tabs: the others stay under Custom views.', { max: String(max) }),
        }}
      />
    </CustomizePanel>
  );

  return (
    <>
      <Tabs value={value} panels="external">
        <TabsList actions={customize || undefined}>
          {tab('installed', t('Active'), '/admin/threat-arsenal')}
          {tab('available', t('Available'), '/admin/threat-arsenal/available')}
          {reviewMode === 'to-review' && tab('to-review', pendingCount > 0 ? `${t('To review')} (${pendingCount})` : t('To review'), reviewPath)}
          {reviewMode === 'my-changes' && tab('my-changes', t('My changes'), reviewPath)}
          {/* Every custom view is a split tab: the name is a real link (one click opens it, ⌘-click opens a
              new browser tab), the arrow beside it opens Rename and Delete (Jakob's Law: GitHub Projects;
              Law of Proximity; NN/g H4 the same arrow on every view, H7 links keep browser shortcuts). */}
          {reviewMode && views.tabs.map(view => (
            <SplitTab
              key={view.id}
              value={`view:${view.id}`}
              label={view.label}
              to={viewPath(view.id)}
              active={openView?.id === view.id}
              menuLabel={t('Options of the view {name}', { name: view.label })}
              menu={(
                <>
                  <MenuItem
                    startIcon={<EditOutlined fontSize="small" />}
                    onSelect={() => setEdit({
                      kind: 'rename',
                      view,
                    })}
                  >
                    {t('Rename view')}
                  </MenuItem>
                  <MenuItem
                    startIcon={<DeleteOutlined fontSize="small" style={deleteStyle} />}
                    style={deleteStyle}
                    onSelect={() => setEdit({
                      kind: 'delete',
                      view,
                    })}
                  >
                    {t('Delete view')}
                  </MenuItem>
                </>
              )}
            />
          ))}
          {reviewMode && (
            <TabsMenuTrigger
              active={!!openView && views.more.includes(openView)}
              menuProps={{
                open: menuOpen,
                onOpenChange: setMenuOpen,
              }}
              menu={(
                <>
                  {/* A click (or Enter) opens the view; hovering it (or →) offers Rename and Delete. */}
                  {views.more.map(view => (
                    <MenuSub key={view.id}>
                      <MenuSubTrigger
                        aria-current={openView?.id === view.id ? 'page' : undefined}
                        onClick={() => openFromMenu(view.id)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault();
                            openFromMenu(view.id);
                          }
                        }}
                        style={openView?.id === view.id ? { fontWeight: 600 } : undefined}
                      >
                        {view.label}
                      </MenuSubTrigger>
                      <MenuSubContent>
                        <MenuItem
                          startIcon={<EditOutlined fontSize="small" />}
                          onSelect={() => setEdit({
                            kind: 'rename',
                            view,
                          })}
                        >
                          {t('Rename view')}
                        </MenuItem>
                        <MenuItem
                          startIcon={<DeleteOutlined fontSize="small" style={deleteStyle} />}
                          style={deleteStyle}
                          onSelect={() => setEdit({
                            kind: 'delete',
                            view,
                          })}
                        >
                          {t('Delete view')}
                        </MenuItem>
                      </MenuSubContent>
                    </MenuSub>
                  ))}
                  {views.more.length > 0 && <MenuSeparator />}
                  <MenuItem asChild startIcon={<AddOutlined fontSize="small" />}>
                    <Link to={`${reviewPath}?${NEW_VIEW_PARAM}=1`}>{t('New custom view')}</Link>
                  </MenuItem>
                </>
              )}
            >
              {t('Custom views')}
            </TabsMenuTrigger>
          )}
        </TabsList>
      </Tabs>
      <ReviewViewDialogs
        edit={edit}
        onClose={() => setEdit(null)}
        onRename={(view, label) => views.update(view.id, { label })}
        onDelete={(view) => {
          views.remove(view.id);
          if (openView?.id === view.id) navigate(reviewPath);
        }}
      />
    </>
  );
};

export default ThreatArsenalTabs;
