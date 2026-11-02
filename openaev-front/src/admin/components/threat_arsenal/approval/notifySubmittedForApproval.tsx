import { Link } from 'react-router';

import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { THREAT_ARSENAL_MY_CHANGES_PATH } from '../threatArsenalDeepLink';

type Translate = (message: string) => string;

/**
 * Tells someone without "Approve content" what just happened to what they saved: it waits for an
 * approver (a new action cannot be used yet; a new version leaves the approved one running), with a
 * link to follow it in "My changes". Says nothing when the save needed no approval (cosmetic edit).
 */
const notifySubmittedForApproval = (t: Translate, action: ThreatArsenalAction | undefined) => {
  const payload = action?.action_payload;
  let message: string | undefined;
  if (payload?.payload_approval_status === 'APPROVED' && payload.payload_pending_version) {
    message = t('Submitted for approval as a new version. The approved version keeps running until an approver decides.');
  } else if (payload?.payload_approval_status === 'PENDING') {
    message = t('Submitted for approval. It can be used once an approver approves it.');
  }
  if (!message) return;
  MESSAGING$.notifySuccess(
    <span>
      {`${message} `}
      <Link to={THREAT_ARSENAL_MY_CHANGES_PATH}>{t('Follow it in My changes')}</Link>
    </span>,
  );
};

export default notifySubmittedForApproval;
