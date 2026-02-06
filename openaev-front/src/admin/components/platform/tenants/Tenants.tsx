import {HomeWorkOutlined} from '@mui/icons-material';
import {List, ListItem, ListItemButton, ListItemIcon, ListItemText} from '@mui/material';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import {buildSearchPagination} from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import {useQueryableWithLocalStorage} from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import {useFormatter} from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import {type TenantOutput} from '../../../../utils/api-types';
import {Can} from '../../../../utils/permissions/PermissionsProvider';
import {ACTIONS, SUBJECTS} from '../../../../utils/permissions/types';
import TenantCreate from './tenant/TenantCreate';
import TenantPopover from './TenantPopover';
import {useTenants} from "./useTenants";
import {getTenantHeaders, LOCAL_STORAGE_KEY_TENANT, TENANT_FILTERS, TENANT_SORTS} from "./tenants.queryable";
import {useMemo} from "react";
import useBodyItemsStyles from "../../../../components/common/queryable/style/style";

const Tenants = () => {
    // Standard hooks
    const {t} = useFormatter();
    const bodyItemsStyles = useBodyItemsStyles();

    const {
        tenants,
        setTenantList,
        loading,
        fetchTenants,
        addTenant,
        updateTenant,
        removeTenant,
    } = useTenants();

    const {
        queryableHelpers,
        searchPaginationInput,
    } = useQueryableWithLocalStorage(LOCAL_STORAGE_KEY_TENANT, buildSearchPagination({sorts: TENANT_SORTS}));
    const headers = useMemo(() => getTenantHeaders(t), [t]);

    return (
        <>
            <Breadcrumbs
                variant="list"
                elements={[{label: t('Platform')}, {
                    label: t('Tenant management'),
                    current: true,
                }]}
            />
            <PaginationComponentV2
                fetch={fetchTenants}
                searchPaginationInput={searchPaginationInput}
                setContent={setTenantList}
                entityPrefix="tenant"
                availableFilterNames={TENANT_FILTERS}
                queryableHelpers={queryableHelpers}
            />
            <List>
                <ListItem
                    divider={false}
                    secondaryAction={<>&nbsp;</>}
                    style={{ paddingTop: 0 }}
                >
                    <ListItemIcon/>
                    <ListItemText
                        style={{textTransform: 'uppercase'}}
                        primary={(
                            <SortHeadersComponentV2
                                headers={headers}
                                sortHelpers={queryableHelpers.sortHelpers}
                                inlineStylesHeaders={{}}
                            />
                        )}
                    />
                </ListItem>
                {loading
                    ? <PaginatedListLoader Icon={HomeWorkOutlined} headers={headers} headerStyles={{}}/>
                    : tenants.map((tenant: TenantOutput) => {
                        return (
                            (
                                <ListItem
                                    key={tenant.tenant_id}
                                    divider
                                    disablePadding
                                    secondaryAction={(
                                        <TenantPopover
                                            inList
                                            tenant={tenant}
                                            actions={['Update', 'Delete']}
                                            onUpdate={updateTenant}
                                            onDelete={removeTenant}
                                        />
                                    )}
                                >
                                    <ListItemButton style={{height: 50}}>
                                        <ListItemIcon>
                                            <HomeWorkOutlined color="primary"/>
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={(
                                                <div style={bodyItemsStyles.bodyItems}>
                                                    {headers.map(header => (
                                                        <div key={header.field}                                style={{
                                                            ...bodyItemsStyles.bodyItem,
                                                        }}>
                                                            {header.value?.(tenant)}
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
                                        />
                                    </ListItemButton>
                                </ListItem>
                            )
                        );
                    })}
            </List>
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANTS}>
                <TenantCreate onCreate={addTenant}/>
            </Can>
        </>
    );
};

export default Tenants;
