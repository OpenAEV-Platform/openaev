import { useEffect, useState } from 'react';

import { fetchCatalogConnectorConfigurations } from '../../../../actions/catalog/catalog-actions';
import { fetchConnectorInstanceConfigurations } from '../../../../actions/connector_instances/connector-instance-actions';
import DOTS from '../../../../constants/Strings';
import type {
  CatalogConnectorConfiguration,
  ConfigurationInput,
  ConnectorInstanceConfiguration,
} from '../../../../utils/api-types';

// The connector "display name" lives in a type-specific configuration key.
export const CONNECTOR_NAME_KEYS = ['COLLECTOR_NAME', 'INJECTOR_NAME', 'EXECUTOR_NAME'];

const useConnectorInstanceForm = (
  isEditing: boolean,
  catalogConnectorId: string,
  connectorInstanceId?: string,
  open?: boolean,
) => {
  const [loading, setLoading] = useState(true);
  const [configurationsDefinitionMap, setConfigurationsDefinitionMap] = useState<Record<string, CatalogConnectorConfiguration>>({});
  const [initialValues, setInitialValues] = useState<ConfigurationInput[]>([] as ConfigurationInput[]);

  useEffect(() => {
    if (!open || !catalogConnectorId) return undefined;

    // Guard against out-of-order responses: when the user switches connectors
    // quickly, a slower earlier fetch must not overwrite the current one's state.
    let cancelled = false;
    // Reset immediately so the form for the previous connector cannot linger
    // (it is gated on `!loading`), then repopulate once the new data arrives.
    setLoading(true);

    const loadConfigurations = async () => {
      const [catalogConfigResponse, instanceConfigResponse] = await Promise.all([
        fetchCatalogConnectorConfigurations(catalogConnectorId),
        isEditing && connectorInstanceId
          ? fetchConnectorInstanceConfigurations(connectorInstanceId)
          : Promise.resolve({ data: [] }),
      ]);
      if (cancelled) return;
      const definition: CatalogConnectorConfiguration[] = catalogConfigResponse.data ?? [];
      const instanceConfigs: ConnectorInstanceConfiguration[] = instanceConfigResponse.data ?? [];
      const defMap = Object.fromEntries(
        definition.map((d: CatalogConnectorConfiguration) => [d.connector_configuration_key, d]),
      );
      setConfigurationsDefinitionMap(defMap);

      const initialConfigurations: ConfigurationInput[] = Object.entries(defMap).map(([key, def]) => {
        const matchingValues = instanceConfigs.find(v => v.connector_instance_configuration_key === key)?.connector_instance_configuration_value;
        // a secret in editing mode must display "DOTS" instead of something else
        const defaultValue = (isEditing && def.connector_configuration_writeonly) ? DOTS : (def.connector_configuration_default ?? '');
        const value = matchingValues ? matchingValues : defaultValue;
        return {
          configuration_key: key,
          configuration_value: def.connector_configuration_type == 'INTEGER' ? value.toString() : value,
        } as ConfigurationInput;
      });
      setInitialValues(initialConfigurations);
      setLoading(false);
    };

    loadConfigurations();
    return () => {
      cancelled = true;
    };
  }, [open, catalogConnectorId, isEditing, connectorInstanceId]);

  return {
    loading,
    configurationsDefinitionMap,
    initialValues,
  };
};

export default useConnectorInstanceForm;
