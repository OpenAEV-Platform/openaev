import { Chip, Tooltip } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { type TenantOutput } from '../utils/api-types';

const useStyles = makeStyles()(theme => ({
  tenantChip: {
    height: theme.spacing(2),
    borderRadius: theme.shape.borderRadius,
    margin: '0 7px 0 0',
    textTransform: 'lowercase',
    width: 100,
    marginBottom: theme.spacing(0),
  },
}));

interface ItemTenantsProps {
  tenants: string[];
  tenantsMap: Record<string, TenantOutput>;
}

const ItemTenants = ({ tenants, tenantsMap }: ItemTenantsProps) => {
  const { classes } = useStyles();

  return (
    <>
      {tenants.map(tenant => (
        <Tooltip key={tenant} title={tenantsMap[tenant]?.tenant_name ?? tenant}>
          <Chip
            key={tenant}
            variant="outlined"
            label={tenantsMap[tenant]?.tenant_name ?? tenant}
            className={classes.tenantChip}
          />
        </Tooltip>
      ))}
    </>
  );
};

export default ItemTenants;
