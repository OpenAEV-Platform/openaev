import { Chip, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';

import type { UserTenantOutput } from '../../utils/api-types';
import { getLabelOfRemainingItems, getRemainingItemsCount, getVisibleItems, truncate } from '../../utils/String';

interface Props {
  tenants: UserTenantOutput[] | undefined;
  limit?: number;
}

const ItemTenants: FunctionComponent<Props> = ({ tenants, limit = 3 }) => {
  const theme = useTheme();

  const orderedTenants = useMemo(
    () => [...(tenants ?? [])].sort((a, b) => (a.tenant_name ?? '').localeCompare(b.tenant_name ?? '')),
    [tenants],
  );

  const visibleTenants = getVisibleItems(orderedTenants, limit) ?? [];
  const tooltipLabel = getLabelOfRemainingItems(orderedTenants, limit, 'tenant_name');
  const remainingCount = getRemainingItemsCount(orderedTenants, visibleTenants) ?? 0;

  if (visibleTenants.length === 0) {
    return <span>-</span>;
  }

  const chipSx = {
    fontSize: theme.typography.caption.fontSize,
    borderRadius: theme.shape.borderRadius,
  };

  return (
    <div style={{
      display: 'inline-flex',
      alignItems: 'center',
      flexWrap: 'nowrap',
      overflow: 'hidden',
      gap: theme.spacing(0.5),
    }}
    >
      {visibleTenants.map(tenant => (
        <Tooltip key={tenant.tenant_id} title={tenant.tenant_name ?? ''}>
          <Chip
            variant="outlined"
            size="small"
            label={truncate(tenant.tenant_name ?? '', 15)}
            sx={chipSx}
          />
        </Tooltip>
      ))}
      {remainingCount > 0 && (
        <Tooltip title={tooltipLabel}>
          <Chip
            variant="outlined"
            size="small"
            label={`+${remainingCount}`}
            sx={chipSx}
          />
        </Tooltip>
      )}
    </div>
  );
};

export default ItemTenants;
