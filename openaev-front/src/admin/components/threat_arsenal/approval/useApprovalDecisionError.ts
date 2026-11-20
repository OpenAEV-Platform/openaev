import { fetchMe } from '../../../../actions/Application';
import { useFormatter } from '../../../../components/i18n';
import { MESSAGING$ } from '../../../../utils/Environment';
import { type Error as ApiError, notifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';
import { useAppDispatch } from '../../../../utils/hooks';

/**
 * Handles an error on an approval decision (approve, reject, move to pending approval). A 403 means
 * "Approve content" was removed from the user's role since the capabilities were loaded: reload them,
 * so the decision buttons disappear, and say why instead of a generic error.
 */
const useApprovalDecisionError = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  return (error: ApiError) => {
    if (error?.status === 403) {
      dispatch(fetchMe());
      MESSAGING$.notifyError(t('You can no longer approve or reject payloads: Approve content was removed from your role.'));
      return;
    }
    notifyErrorHandler(error);
  };
};

export default useApprovalDecisionError;
