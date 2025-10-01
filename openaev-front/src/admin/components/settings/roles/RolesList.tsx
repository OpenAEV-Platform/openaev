import { HelpOutlineOutlined, SecurityOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type ReactNode } from 'react';
import { useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import type { QueryableHelpers } from '../../../../components/common/queryable/QueryableHelpers';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type RoleOutput } from '../../../../utils/api-types';

interface RolesListProps {
  roles: RoleOutput[];
  queryableHelpers: QueryableHelpers;
  loading: boolean;
  secondaryAction?: (role: RoleOutput) => ReactNode;
}

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
  container: { display: 'flex' },
  bodyItems: { flexGrow: 1 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  role_name: { width: '50%' },
  role_created_at: { width: '25%' },
  role_updated_at: { width: '25%' },
};

const RolesList = ({
  roles,
  queryableHelpers,
  loading,
  secondaryAction,
}: RolesListProps) => {
  const { t, nsdt } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const headers = useMemo(() => [
    {
      field: 'role_name',
      label: t('Name'),
      isSortable: true,
      value: (role: RoleOutput) => role.role_name,
    },
    {
      field: 'role_created_at',
      label: t('Platform creation date'),
      isSortable: true,
      value: (role: RoleOutput) => {
        if (!role.role_created_at) {
          return '-';
        }
        return <>{(nsdt(role.role_created_at))}</>;
      },
    },
    {
      field: 'role_updated_at',
      label: t('Modification date'),
      isSortable: true,
      value: (role: RoleOutput) => {
        if (!role.role_updated_at) {
          return '-';
        }
        return <>{(nsdt(role.role_updated_at))}</>;
      },
    },
  ], []);
  return (
    <List>
      <ListItem
        classes={{ root: classes.itemHead }}
        divider={false}
        style={{ paddingTop: 0 }}
        secondaryAction={<>&nbsp;</>}
      >
        <ListItemIcon />
        <ListItemText
          primary={(
            <SortHeadersComponentV2
              headers={headers}
              inlineStylesHeaders={inlineStyles}
              sortHelpers={queryableHelpers.sortHelpers}
            />
          )}
        />
      </ListItem>
      {loading
        ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
        : (
            <div>
              {roles.map((role: RoleOutput) => (
                <ListItem
                  key={role.role_id}
                  classes={{ root: classes.item }}
                  divider
                  secondaryAction={secondaryAction && secondaryAction(role)}

                >
                  <ListItemIcon>
                    <SecurityOutlined sx={{ color: '#96F426' }} />
                  </ListItemIcon>
                  <ListItemText primary={(
                    <div style={bodyItemsStyles.bodyItems}>
                      {headers.map(header => (
                        <div
                          key={header.field}
                          style={{
                            ...bodyItemsStyles.bodyItem,
                            ...inlineStyles[header.field],
                          }}
                        >
                          {header.value && header.value(role)}
                        </div>
                      ))}

                    </div>

                  )}
                  />
                </ListItem>
              ),
              )}
            </div>
          )}
    </List>
  );
};

export default RolesList;
