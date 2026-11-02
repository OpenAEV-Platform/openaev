import { Alert, Button, Paper, Spinner, Text } from '@filigran/design-system';
import { FactCheckOutlined, GroupsOutlined, SyncOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { Link } from 'react-router';

import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { ApprovalQueueGroup } from './approval/approvalQueue';
import { isFromCollector, useApprovalQueue, useQueueDrawer } from './approval/useApprovalQueue';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import ThreatArsenalPageHeader from './ThreatArsenalPageHeader';

/**
 * "To review": one queue of everything waiting for a decision (new actions and new versions of
 * approved actions), grouped by where it comes from, so an approver finds pending work without
 * filtering the whole arsenal. Opening a row (or a "pending approval" notification) shows the
 * version to decide on.
 */
const ThreatArsenalToReview = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useAbility();
  const canSeeRoles = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_USERS_GROUPS_AND_ROLES);

  const { items, remove } = useApprovalQueue(['PENDING']);
  const drawer = useQueueDrawer();

  const fromPeople = (items ?? []).filter(item => !isFromCollector(item));
  const fromCollectors = (items ?? []).filter(isFromCollector);

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
        <ApprovalQueueGroup title={t('From people')} icon={GroupsOutlined} items={fromPeople} actionLabel={t('Review')} onOpen={drawer.open} />
        <ApprovalQueueGroup title={t('From collectors')} icon={SyncOutlined} items={fromCollectors} actionLabel={t('Review')} onOpen={drawer.open} />
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

      {drawer.selected && (
        <ThreatArsenalInformationDrawer
          open
          onClose={drawer.close}
          threatArsenalAction={drawer.selected}
          onApprovalChanged={(actionId, status, pendingVersion) => {
            // Decided: it leaves the queue.
            if (status !== 'PENDING' && !pendingVersion) remove(actionId);
          }}
        />
      )}
    </div>
  );
};

export default ThreatArsenalToReview;
