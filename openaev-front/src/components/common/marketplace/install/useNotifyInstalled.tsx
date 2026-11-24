import { Link } from 'react-router';

import { MESSAGING$ } from '../../../../utils/Environment';
import { useFormatter } from '../../../i18n';

// With the page of what was installed, the message links to it.
const useNotifyInstalled = () => {
  const { t } = useFormatter();
  return (name: string, version: string, openUrl?: string) => {
    const message = t('{name} v{version} installed', {
      name,
      version,
    });
    MESSAGING$.notifySuccess(openUrl
      ? (
          <span>
            {`${message} `}
            <Link to={openUrl}>{t('Open')}</Link>
          </span>
        )
      : message);
  };
};

export default useNotifyInstalled;
