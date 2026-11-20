import { MESSAGING$ } from '../../../../utils/Environment';
import { useFormatter } from '../../../i18n';

const useNotifyInstalled = () => {
  const { t } = useFormatter();
  return (name: string, version: string) => MESSAGING$.notifySuccess(t('{name} v{version} installed', {
    name,
    version,
  }));
};

export default useNotifyInstalled;
