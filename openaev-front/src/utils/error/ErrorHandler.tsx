import { useEffect } from 'react';

import { useFormatter } from '../../components/i18n';
import { MESSAGING$ } from '../Environment';
import useEnterpriseEdition from '../hooks/useEnterpriseEdition';
import { type Error, setNotifyErrorHandler } from './errorHandlerUtil';

const ErrorHandler = () => {
  const { openDialog, setEEFeatureDetectedInfo } = useEnterpriseEdition();
  const { t } = useFormatter();

  useEffect(() => {
    setNotifyErrorHandler((error: Error) => {
      if (error.status === 401 || error.status === 404) return;

      if (error.status === 403 && error.message === 'LICENSE_RESTRICTION') {
        const messages = error?.errors?.children?.message?.errors;
        setEEFeatureDetectedInfo(Array.isArray(messages) ? messages.join(', ') : '');
        openDialog();
      } else if (error.status === 409) {
        MESSAGING$.notifyError(t('The element already exists'));
      } else if (error.status === 400) {
        if (error.message) {
          MESSAGING$.notifyError(t(error.message));
        } else {
          MESSAGING$.notifyError(t('Bad request'));
        }
      } else if (error.status === 500) {
        MESSAGING$.notifyError(t('Internal error'));
      } else if (error.message) {
        MESSAGING$.notifyError(error.message);
      } else {
        MESSAGING$.notifyError(t('Something went wrong. Please refresh the page or try again later.'));
      }
    });
  }, []);

  return null;
};

export default ErrorHandler;
