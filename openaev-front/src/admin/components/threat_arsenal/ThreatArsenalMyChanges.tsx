import { Paper, Spinner, Text } from '@filigran/design-system';
import { DoNotDisturbOnOutlined, EditNoteOutlined, HourglassEmptyOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';

import { type UserHelper } from '../../../actions/helper';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { ApprovalQueueGroup } from './approval/approvalQueue';
import { displayedApprovalStatus } from './approval/approvalStatusUtils';
import { type QueueItem, useApprovalQueue, useQueueDrawer } from './approval/useApprovalQueue';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import ThreatArsenalPageHeader from './ThreatArsenalPageHeader';

/**
 * "My changes", for people who edit without approving: what they submitted that is still waiting,
 * and what was rejected (with the reason), so they never have to search the arsenal to follow their
 * own work. Same rows as the approver's "To review", so a version looks the same on both sides.
 */
const ThreatArsenalMyChanges = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const me = useHelper((helper: UserHelper) => helper.getMe());

  const isMine = (item: QueueItem) => !!me?.user_id && item.full?.action_last_modified_by === me.user_id;
  const { items } = useApprovalQueue(['PENDING', 'REJECTED'], isMine);
  const drawer = useQueueDrawer();

  const statusOf = (item: QueueItem) => displayedApprovalStatus(
    item.full?.action_approval_status ?? item.action.action_payload?.payload_approval_status,
    !!item.full?.action_pending_version,
  );
  const waiting = (items ?? []).filter(item => statusOf(item) === 'PENDING');
  const rejected = (items ?? []).filter(item => statusOf(item) === 'REJECTED');

  const renderList = () => {
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
            <EditNoteOutlined />
            <Text as="h2" variant="title-xs" style={{ margin: 0 }}>{t('Nothing waiting')}</Text>
            <Text variant="content-compact" className="text-default-secondary">
              {t('Actions you create or change show up here until an approver decides.')}
            </Text>
          </div>
        </Paper>
      );
    }
    return (
      <>
        <ApprovalQueueGroup title={t('Rejected')} icon={DoNotDisturbOnOutlined} items={rejected} actionLabel={t('Open')} showDecision onOpen={drawer.open} />
        <ApprovalQueueGroup title={t('Waiting for approval')} icon={HourglassEmptyOutlined} items={waiting} actionLabel={t('Open')} showDecision onOpen={drawer.open} />
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
          label: t('My changes'),
          current: true,
        }]}
      />
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
      >
        <ThreatArsenalPageHeader active="my-changes" />
        {renderList()}
      </div>

      {drawer.selected && (
        <ThreatArsenalInformationDrawer
          open
          initialTab="versions"
          onClose={drawer.close}
          threatArsenalAction={drawer.selected}
        />
      )}
    </div>
  );
};

export default ThreatArsenalMyChanges;
