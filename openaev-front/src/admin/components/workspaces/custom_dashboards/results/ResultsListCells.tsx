import { Chip, Skeleton } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { directFetchInjectorContract } from '../../../../../actions/InjectorContracts';
import colorStyles from '../../../../../components/Color';
import { useFormatter } from '../../../../../components/i18n';
import { type EsInject, type InjectorContract } from '../../../../../utils/api-types';
import { isNotEmptyField } from '../../../../../utils/utils';
import { type AssetCategory, humanizeEnum } from '../../../assets/asset-categories';
import AssetCategoryIcon from '../../../assets/AssetCategoryIcon';
import InjectIcon from '../../../common/injects/InjectIcon';
import { SCENARIO_NOT_SCHEDULED_STATUS, SCENARIO_SCHEDULED_STATUS } from '../../../scenarios/scenario/ScenarioStatus';

/**
 * Cell components of the results explorer entity lists, kept in a
 * components-only module so react-refresh can hot-reload them (the sibling
 * resultsListConfig module exports plain configuration objects).
 */

// Same uppercase type cell as the canonical Findings list page.
export const FindingTypeCell: FunctionComponent<{ value: string }> = ({ value }) => {
  const { t } = useFormatter();
  return (
    <span style={{
      fontWeight: 600,
      textTransform: 'uppercase',
      fontSize: 11,
      letterSpacing: '0.05em',
    }}
    >
      {t(value)}
    </span>
  );
};

// Same category cell as the canonical Endpoints list page.
export const AssetCategoryCell: FunctionComponent<{ category?: string }> = ({ category }) => {
  const { t } = useFormatter();
  return (
    <span style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
    }}
    >
      <AssetCategoryIcon category={category as AssetCategory} fontSize="small" />
      {category ? t(humanizeEnum(category)) : '-'}
    </span>
  );
};

// Same status chip as the canonical Scenarios list page (ScenarioStatus works
// on the full JPA scenario; the ES document only exposes the derived status).
export const ScenarioStatusCell: FunctionComponent<{ status?: string }> = ({ status }) => {
  const { t } = useFormatter();
  const scheduled = status === 'SCHEDULED';
  return (
    <Chip
      sx={{
        fontSize: 12,
        height: 20,
        float: 'left',
        textTransform: 'uppercase',
        borderRadius: 1,
        width: 120,
      }}
      style={scheduled ? colorStyles.green : colorStyles.grey}
      label={t(scheduled ? SCENARIO_SCHEDULED_STATUS : SCENARIO_NOT_SCHEDULED_STATUS)}
    />
  );
};

// Same leading icon as the canonical Injects lists: the icon comes from the
// injector contract (payload collector / payload type / injector type),
// resolved lazily and cached so each contract is only fetched once.
const injectorContractCache = new Map<string, Promise<InjectorContract | null>>();
const fetchContractCached = (contractId: string): Promise<InjectorContract | null> => {
  let cached = injectorContractCache.get(contractId);
  if (!cached) {
    cached = directFetchInjectorContract(contractId)
      .then((result: { data: InjectorContract }) => result.data)
      .catch(() => null);
    injectorContractCache.set(contractId, cached);
  }
  return cached;
};

export const InjectRowIcon: FunctionComponent<{ element: EsInject }> = ({ element }) => {
  const contractId = element.base_inject_contract_side;
  const [contract, setContract] = useState<InjectorContract | null>(null);
  const [loading, setLoading] = useState(Boolean(contractId));
  useEffect(() => {
    if (!contractId) {
      return undefined;
    }
    let active = true;
    fetchContractCached(contractId).then((resolved) => {
      if (active) {
        setContract(resolved);
        setLoading(false);
      }
    });
    return () => {
      active = false;
    };
  }, [contractId]);
  if (loading) {
    return <Skeleton variant="circular" width={24} height={24} />;
  }
  const payload = contract?.injector_contract_payload;
  return (
    <InjectIcon
      isPayload={isNotEmptyField(payload)}
      type={payload
        ? payload.payload_collector_type || payload.payload_type
        : contract?.injector_contract_injector_type}
    />
  );
};
