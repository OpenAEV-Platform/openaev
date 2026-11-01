import { Alert, Avatar, Button, Card, CardHeader, CardTitle, Chip, Paper, Spinner, Text } from '@filigran/design-system';
import { FactCheckOutlined, GroupsOutlined, SyncOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type ComponentType, type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { fetchThreatArsenalAction, searchThreatArsenalActions } from '../../../actions/threat_arsenals/threatArsenal-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { type ThreatArsenalAction, type ThreatArsenalActionFullOutput } from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { toThreatArsenalAction } from './threatArsenalDeepLink';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import ThreatArsenalPageHeader from './ThreatArsenalPageHeader';

// Enough for a review queue; the queue is meant to be emptied, not browsed.
const QUEUE_SIZE = 100;

const pendingQueueInput = () => buildSearchPagination({
  size: QUEUE_SIZE,
  filterGroup: {
    mode: 'and',
    filters: [{
      id: generateFilterId(),
      // Matches never-approved actions and approved actions with a new version waiting.
      key: 'action_payload_approval_status',
      operator: 'eq',
      mode: 'or',
      values: ['PENDING'],
    }],
  },
  sorts: [{
    property: 'action_updated_at',
    direction: 'ASC',
  }],
});

interface ReviewItem {
  action: ThreatArsenalAction;
  full?: ThreatArsenalActionFullOutput;
}

const isFromCollector = (item: ReviewItem) => item.full?.action_pending_version?.version_origin === 'COLLECTOR'
  || item.full?.action_approval_latest?.approval_origin === 'COLLECTOR'
  || item.action.action_payload?.payload_collector_type != null;

interface RowProps {
  item: ReviewItem;
  onOpen: () => void;
}

/** One pending version in the queue: what it is, who submitted it, when; opens the review panel. */
const ReviewRow: FunctionComponent<RowProps> = ({ item, onOpen }) => {
  const { t, nsdt, tPick } = useFormatter();
  const theme = useTheme();
  const pendingVersion = item.full?.action_pending_version;
  const isNewVersion = !!pendingVersion || !!item.action.action_payload?.payload_pending_version;
  const latest = item.full?.action_approval_latest;
  const author = pendingVersion?.version_author_name ?? latest?.approval_actor_name;
  const submittedAt = pendingVersion?.version_created_at ?? latest?.approval_created_at ?? item.action.injector_contract_updated_at;
  const versionLabel = pendingVersion?.version_number ? `v${pendingVersion.version_number}` : undefined;

  return (
    <Card padding={16} gap={8} onClick={onOpen}>
      <CardHeader action={<Button size="sm" priority="secondary" onClick={onOpen}>{t('Review')}</Button>}>
        <CardTitle as="h3">{tPick(item.action.action_labels)}</CardTitle>
      </CardHeader>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        flexWrap: 'wrap',
      }}
      >
        <Chip
          label={isNewVersion
            ? t('New version {version}', { version: versionLabel ?? '' }).trim()
            : t('New action')}
          severity="medium"
        />
        {isNewVersion && (
          <Text variant="content-compact" className="text-default-secondary">
            {t('The approved version keeps running until you decide')}
          </Text>
        )}
        <span style={{
          marginLeft: 'auto',
          display: 'inline-flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
        >
          {isFromCollector(item)
            ? <Text variant="content-compact">{t('Synchronized by a collector')}</Text>
            : <Avatar name={author} size="xs" showName />}
          {submittedAt && (
            <Text variant="content-compact" className="text-default-secondary">{nsdt(submittedAt)}</Text>
          )}
        </span>
      </div>
    </Card>
  );
};

interface GroupProps {
  title: string;
  icon: ComponentType<{ fontSize?: 'small' }>;
  items: ReviewItem[];
  onOpen: (item: ReviewItem) => void;
}

const ReviewGroup: FunctionComponent<GroupProps> = ({ title, icon: Icon, items, onOpen }) => {
  const theme = useTheme();
  if (items.length === 0) return null;
  return (
    <section
      aria-label={title}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(1),
      }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
      }}
      >
        <Icon fontSize="small" />
        <Text as="h2" variant="title-xs" style={{ margin: 0 }}>{`${title} (${items.length})`}</Text>
      </div>
      {items.map(item => (
        <ReviewRow key={item.action.injector_contract_id} item={item} onOpen={() => onOpen(item)} />
      ))}
    </section>
  );
};

/**
 * "To review": one queue of everything waiting for a decision (new actions and new versions of
 * approved actions), grouped by where it comes from, so an approver finds pending work without
 * filtering the whole arsenal. Opening a row shows the version to decide on.
 */
const ThreatArsenalToReview = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useAbility();
  const canSeeRoles = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_USERS_GROUPS_AND_ROLES);

  const [items, setItems] = useState<ReviewItem[] | null>(null);
  const [selected, setSelected] = useState<ThreatArsenalAction | null>(null);

  const load = useCallback(() => {
    searchThreatArsenalActions(pendingQueueInput())
      .then(async (response) => {
        const actions = (response.data?.content ?? []) as ThreatArsenalAction[];
        // Who submitted each version and from where is on the detail; the queue stays small.
        const details = await Promise.all(actions.map(action => fetchThreatArsenalAction(action.injector_contract_id)
          .then(detail => detail.data as ThreatArsenalActionFullOutput)
          .catch(() => undefined)));
        setItems(actions.map((action, index) => ({
          action,
          full: details[index],
        })));
      })
      .catch(() => setItems([]));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const fromPeople = (items ?? []).filter(item => !isFromCollector(item));
  const fromCollectors = (items ?? []).filter(isFromCollector);

  const open = (item: ReviewItem) => setSelected(item.full ? toThreatArsenalAction(item.full) : item.action);

  const renderQueue = () => {
    if (items === null) {
      return (
        <div style={{
          display: 'flex',
          justifyContent: 'center',
          padding: theme.spacing(4),
        }}
        >
          <Spinner />
        </div>
      );
    }
    if (items.length === 0) {
      return (
        <Paper padding={32} title={undefined}>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: theme.spacing(1),
            textAlign: 'center',
          }}
          >
            <FactCheckOutlined />
            <Text as="h2" variant="title-xs" style={{ margin: 0 }}>{t('Nothing to review')}</Text>
            <Text variant="content-compact" className="text-default-secondary">
              {t('New actions and new versions waiting for approval show up here.')}
            </Text>
          </div>
        </Paper>
      );
    }
    return (
      <>
        <ReviewGroup title={t('From people')} icon={GroupsOutlined} items={fromPeople} onOpen={open} />
        <ReviewGroup title={t('From collectors')} icon={SyncOutlined} items={fromCollectors} onOpen={open} />
      </>
    );
  };

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
          label: t('To review'),
          current: true,
        }]}
      />
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
      >
        <ThreatArsenalPageHeader active="to-review" />
        <Alert
          severity="info"
          title={t('Approval needs a second person only for roles without "Approve content"')}
          description={t('People whose role holds "Approve content" approve their own changes automatically. Remove it from a role to make its members submit changes for review.')}
          action={canSeeRoles
            ? (
                <Button size="sm" priority="secondary" asChild>
                  <Link to="/admin/settings/security/roles">{t('Review roles')}</Link>
                </Button>
              )
            : undefined}
        />
        {renderQueue()}
      </div>

      {selected && (
        <ThreatArsenalInformationDrawer
          open
          onClose={() => setSelected(null)}
          threatArsenalAction={selected}
          onApprovalChanged={(actionId, status, pendingVersion) => {
            // Decided: it leaves the queue.
            if (status !== 'PENDING' && !pendingVersion) {
              setItems(prev => (prev ?? []).filter(item => item.action.injector_contract_id !== actionId));
            }
          }}
        />
      )}
    </div>
  );
};

export default ThreatArsenalToReview;
