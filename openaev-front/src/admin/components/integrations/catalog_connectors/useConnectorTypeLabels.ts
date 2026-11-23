import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItemType } from './catalog-facets';

const useConnectorTypeLabels = (): Record<ConnectorItemType, string> => {
  const { t } = useFormatter();
  return {
    COLLECTOR: t('Collector'),
    INJECTOR: t('Injector'),
    EXECUTOR: t('Executor'),
    SECRETS_PROVIDER: t('Secrets Provider'),
  };
};

export default useConnectorTypeLabels;
