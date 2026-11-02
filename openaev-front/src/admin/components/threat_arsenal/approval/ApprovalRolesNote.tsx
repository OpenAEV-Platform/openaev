import { Alert, Button } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

const DISMISSED_KEY = 'threat-arsenal:approval-roles-note-dismissed';

const readDismissed = () => {
  try {
    return window.localStorage.getItem(DISMISSED_KEY) === 'true';
  } catch {
    return false;
  }
};

/**
 * For the people who set up roles only: after the upgrade every role that edits actions also holds
 * "Approve content", so changes are approved automatically and nobody needs a second person. This
 * says how to turn the second-person check on. Approvers who cannot change roles never see it, and
 * an admin can dismiss it once roles are set.
 */
const ApprovalRolesNote = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useAbility();
  const [dismissed, setDismissed] = useState(readDismissed);

  if (dismissed || !ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_USERS_GROUPS_AND_ROLES)) {
    return null;
  }
  const dismiss = () => {
    setDismissed(true);
    try {
      window.localStorage.setItem(DISMISSED_KEY, 'true');
    } catch {
      // Storage unavailable: hidden for this visit only.
    }
  };
  return (
    <Alert
      severity="info"
      title={t('Approval needs a second person only for roles without "Approve content"')}
      description={t('People whose role holds "Approve content" approve their own changes automatically. Remove it from a role to make its members submit changes for review.')}
      action={(
        <div style={{
          display: 'flex',
          gap: theme.spacing(1),
        }}
        >
          <Button size="sm" priority="tertiary" onClick={dismiss}>{t('Dismiss')}</Button>
          <Button size="sm" priority="secondary" asChild>
            <Link to="/admin/settings/security/roles">{t('Review roles')}</Link>
          </Button>
        </div>
      )}
    />
  );
};

export default ApprovalRolesNote;
