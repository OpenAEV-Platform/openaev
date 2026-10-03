import { Button } from '@filigran/design-system';
import { SettingsOutlined, VerifiedUserOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';
import { Link } from 'react-router';

import { searchIocValidations } from '../../../actions/ioc_validations/ioc-validation-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../components/common/SortHeadersList';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { type IocValidationSimpleOutput, type SearchPaginationInput } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import AtomicTestingsTabs from '../atomic_testings/AtomicTestingsTabs';
import IocValidationStatusChip from './IocValidationStatusChip';
import { IOC_VALIDATION_BASE_URL, IOC_VALIDATION_SETTINGS_URL, iocValidationTestKindLabel } from './iocValidationUtils';

const inlineStyles: Record<string, CSSProperties> = {
  ioc_validation_name: { width: '18%' },
  ioc_validation_status: { width: '11%' },
  ioc_validation_requested_by: { width: '10%' },
  ioc_validation_requested_test_kinds: { width: '13%' },
  ioc_validation_iocs_count: { width: '6%' },
  ioc_validation_pairs_count: { width: '6%' },
  ioc_validation_prevented_count: { width: '7%' },
  ioc_validation_detected_count: { width: '7%' },
  ioc_validation_missed_count: { width: '6%' },
  ioc_validation_error_count: { width: '6%' },
  ioc_validation_created_at: { width: '10%' },
};

const AVAILABLE_FILTER_NAMES = ['ioc_validation_status'];

const IocValidations = () => {
  const { t, fldt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();

  const [loading, setLoading] = useState<boolean>(true);
  const [iocValidations, setIocValidations] = useState<IocValidationSimpleOutput[]>([]);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'ioc-validations',
    buildSearchPagination({ sorts: initSorting('ioc_validation_created_at', 'DESC') }),
  );

  const searchIocValidationsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchIocValidations(input).finally(() => setLoading(false));
  };

  const headers: Header[] = useMemo(() => [
    {
      field: 'ioc_validation_name',
      label: 'Name',
      isSortable: true,
      value: (iocValidation: IocValidationSimpleOutput) => iocValidation.ioc_validation_name,
    },
    {
      field: 'ioc_validation_status',
      label: 'Status',
      isSortable: true,
      value: (iocValidation: IocValidationSimpleOutput) => <IocValidationStatusChip status={iocValidation.ioc_validation_status} />,
    },
    {
      field: 'ioc_validation_requested_by',
      label: 'Requested by',
      isSortable: true,
      value: (iocValidation: IocValidationSimpleOutput) => iocValidation.ioc_validation_requested_by || '-',
    },
    {
      field: 'ioc_validation_requested_test_kinds',
      label: 'Requested tests',
      isSortable: false,
      value: (iocValidation: IocValidationSimpleOutput) => (iocValidation.ioc_validation_requested_test_kinds.length > 0
        ? iocValidation.ioc_validation_requested_test_kinds.map(kind => t(iocValidationTestKindLabel(kind))).join(', ')
        : '-'),
    },
    {
      field: 'ioc_validation_iocs_count',
      label: 'IOCs',
      isSortable: false,
      value: (iocValidation: IocValidationSimpleOutput) => String(iocValidation.ioc_validation_iocs_count),
    },
    {
      field: 'ioc_validation_pairs_count',
      label: 'Indicator-platform pairs',
      isSortable: false,
      value: (iocValidation: IocValidationSimpleOutput) => String(iocValidation.ioc_validation_pairs_count),
    },
    {
      field: 'ioc_validation_prevented_count',
      label: 'Prevented',
      isSortable: false,
      value: (iocValidation: IocValidationSimpleOutput) => String(iocValidation.ioc_validation_prevented_count),
    },
    {
      field: 'ioc_validation_detected_count',
      label: 'Detected',
      isSortable: false,
      value: (iocValidation: IocValidationSimpleOutput) => String(iocValidation.ioc_validation_detected_count),
    },
    {
      field: 'ioc_validation_missed_count',
      label: 'Missed',
      isSortable: false,
      value: (iocValidation: IocValidationSimpleOutput) => String(iocValidation.ioc_validation_missed_count),
    },
    {
      field: 'ioc_validation_error_count',
      label: 'Errors',
      isSortable: false,
      value: (iocValidation: IocValidationSimpleOutput) => String(iocValidation.ioc_validation_error_count),
    },
    {
      field: 'ioc_validation_created_at',
      label: 'Created',
      isSortable: true,
      value: (iocValidation: IocValidationSimpleOutput) => fldt(iocValidation.ioc_validation_created_at),
    },
  ], [fldt, t]);

  return (
    <section>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Atomic testings'),
          link: '/admin/atomic_testings',
        }, {
          label: t('IOC validations'),
          current: true,
        }]}
      />
      <AtomicTestingsTabs />
      <PaginationComponentV2
        fetch={searchIocValidationsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setIocValidations}
        entityPrefix="ioc_validation"
        availableFilterNames={AVAILABLE_FILTER_NAMES}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Can I={ACTIONS.ACCESS} a={SUBJECTS.TENANT_SETTINGS}>
            <Button asChild priority="secondary">
              <Link to={IOC_VALIDATION_SETTINGS_URL}>
                <SettingsOutlined fontSize="small" />
                {t('Safety settings')}
              </Link>
            </Button>
          </Can>
        )}
      />
      <List>
        <ListItem
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
          ? <PaginatedListLoader Icon={VerifiedUserOutlined} headers={headers} headerStyles={inlineStyles} />
          : iocValidations.map(iocValidation => (
              <ListItem
                key={iocValidation.ioc_validation_id}
                disablePadding
                divider
              >
                <ListItemButton
                  sx={{ height: 50 }}
                  component={Link}
                  to={`${IOC_VALIDATION_BASE_URL}/${iocValidation.ioc_validation_id}`}
                >
                  <ListItemIcon>
                    <VerifiedUserOutlined color="primary" />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div style={bodyItemsStyles.bodyItems}>
                        {headers.map(header => (
                          <div
                            key={header.field}
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles[header.field],
                            }}
                          >
                            {header.value?.(iocValidation)}
                          </div>
                        ))}
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            ))}
      </List>
      {!loading && iocValidations.length === 0 && (
        <Empty
          icon={VerifiedUserOutlined}
          message={t('No IOC validation request yet.')}
          hint={t('IOC validation requests are sent from OpenCTI and wait here for approval before any test runs.')}
        />
      )}
    </section>
  );
};

export default IocValidations;
