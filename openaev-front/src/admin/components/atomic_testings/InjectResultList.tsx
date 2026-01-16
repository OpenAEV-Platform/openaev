import { CloudUploadOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText, ToggleButton, Tooltip } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { importAtomicTesting } from '../../../actions/atomic_testings/atomic-testing-actions';
import { type Page } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { type QueryableHelpers } from '../../../components/common/queryable/QueryableHelpers';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { type Header } from '../../../components/common/SortHeadersList';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import ItemDomains from '../../../components/ItemDomains';
import ItemStatus from '../../../components/ItemStatus';
import ItemTargets from '../../../components/ItemTargets';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { type InjectResultOutput, type SearchPaginationInput } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isNotEmptyField } from '../../../utils/utils';
import InjectIcon from '../common/injects/InjectIcon';
import InjectImportJsonDialog from '../common/injects/InjectImportJsonDialog';
import InjectorContract from '../common/injects/InjectorContract';
import AtomicTestingPopover from './atomic_testing/AtomicTestingPopover';
import AtomicTestingResult from './atomic_testing/AtomicTestingResult';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  'inject_type': { width: '10%' },
  'inject_title': { width: '15%' },
  'inject_contract_domains': { width: '15%' },
  'inject_status.tracking_sent_date': { width: '15%' },
  'inject_status': { width: '10%' },
  'inject_targets': { width: '15%' },
  'inject_expectations': { width: '10%' },
  'inject_updated_at': { width: '10%' },
};

interface Props {
  showActions?: boolean;
  fetchInjects: (input: SearchPaginationInput) => Promise<{ data: Page<InjectResultOutput> }>;
  goTo: (injectId: string) => string;
  queryableHelpers: QueryableHelpers;
  searchPaginationInput: SearchPaginationInput;
  availableFilterNames?: string[];
  contextId?: string;
}

const InjectResultList: FunctionComponent<Props> = ({
  showActions,
  fetchInjects,
  goTo,
  queryableHelpers,
  searchPaginationInput,
  contextId,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, fldt, tPick, nsdt } = useFormatter();

  const [loading, setLoading] = useState<boolean>(true);
  const [openJsonImportDialog, setOpenJsonImportDialog] = useState(false);
  const [reloadCount, setReloadCount] = useState(0);

  // Filter and sort hook
  const availableFilterNames = [
    'inject_attack_patterns',
    'inject_kill_chain_phases',
    'inject_tags',
    'inject_title',
    'inject_type',
    'inject_updated_at',
    'inject_assets',
    'inject_asset_groups',
    'inject_teams',
    'inject_contract_domains',
  ];
  const [injects, setInjects] = useState<InjectResultOutput[]>([]);

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'inject_type',
      label: 'Type',
      isSortable: false,
      value: (injectResultOutput: InjectResultOutput) => {
        if (injectResultOutput.inject_injector_contract) {
          return (
            <InjectorContract variant="list" label={tPick(injectResultOutput.inject_injector_contract?.injector_contract_labels)} />
          );
        }
        return <InjectorContract variant="list" label={t('Deleted')} deleted={true} />;
      },
    },
    {
      field: 'inject_title',
      label: 'Name',
      isSortable: true,
      value: (injectResultOutput: InjectResultOutput) => {
        return <>{injectResultOutput.inject_title}</>;
      },
    },
    {
      field: 'inject_contract_domains',
      label: t('Domains'),
      isSortable: true,
      value: (injectResultOutput: InjectResultOutput) => {
        return injectResultOutput.inject_contract_domains?.length
          ? (
              <ItemDomains domains={injectResultOutput.inject_contract_domains} variant="reduced-view" />
            )
          : <></>;
      },
    },
    {
      field: 'inject_status.tracking_sent_date',
      label: 'Execution Date',
      isSortable: false,
      value: (injectResultOutput: InjectResultOutput) => {
        const trackingDate = injectResultOutput.inject_status?.tracking_sent_date;
        return <>{trackingDate ? fldt(trackingDate) : '-'}</>;
      },
    },
    {
      field: 'inject_status',
      label: 'Execution status',
      isSortable: false,
      value: (injectResultOutput: InjectResultOutput) => {
        return (<ItemStatus isInject status={injectResultOutput.inject_status?.status_name} label={t(injectResultOutput.inject_status?.status_name || '-')} variant="inList" />);
      },
    },
    {
      field: 'inject_targets',
      label: 'Target',
      isSortable: false,
      value: (injectResultOutput: InjectResultOutput) => {
        return (<ItemTargets targets={injectResultOutput.inject_targets} />);
      },
    },
    {
      field: 'inject_expectations',
      label: 'Global score',
      isSortable: false,
      value: (injectResultOutput: InjectResultOutput) => {
        return (
          <AtomicTestingResult expectations={injectResultOutput.inject_expectation_results} />
        );
      },
    },
    {
      field: 'inject_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (injectResultOutput: InjectResultOutput) => {
        return <>{nsdt(injectResultOutput.inject_updated_at)}</>;
      },
    },
  ], []);

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return fetchInjects(input).finally(() => setLoading(false));
  };

  const handleOpenJsonImportDialog = () => {
    setOpenJsonImportDialog(true);
  };
  const handleCloseJsonImportDialog = () => {
    setOpenJsonImportDialog(false);
  };
  const handleSubmitJsonImportFile = (values: { file: File }) => {
    importAtomicTesting(values.file).then(() => {
      handleCloseJsonImportDialog();
      setReloadCount(prev => prev + 1);
    });
  };

  return (
    <>
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setInjects}
        entityPrefix="inject"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        contextId={contextId}
        reloadContentCount={reloadCount}
        topBarButtons={showActions ? (
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
            <Tooltip title={t('inject_import_json_action')}>
              <ToggleButton
                value="import"
                aria-label="import"
                size="small"
                onClick={handleOpenJsonImportDialog}
              >
                <CloudUploadOutlined
                  color="primary"
                  fontSize="small"
                />
              </ToggleButton>
            </Tooltip>
          </Can>
        ) : null}
      />
      <InjectImportJsonDialog open={openJsonImportDialog} handleClose={handleCloseJsonImportDialog} handleSubmit={handleSubmitJsonImportFile} />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
          secondaryAction={showActions ? <>&nbsp;</> : null}
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
        {
          loading
            ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
            : injects.map((injectResultOutput) => {
                return (
                  <ListItem
                    key={injectResultOutput.inject_id}
                    divider
                    secondaryAction={showActions ? (
                      <AtomicTestingPopover
                        atomic={injectResultOutput}
                        actions={['Duplicate', 'Export', 'Delete']}
                        onDelete={result => setInjects(injects.filter(e => e.inject_id !== result))}
                        inList
                      />
                    ) : null}
                    disablePadding
                  >
                    <ListItemButton
                      component={Link}
                      classes={{ root: classes.item }}
                      to={goTo(injectResultOutput.inject_id)}
                    >
                      <ListItemIcon>
                        <InjectIcon
                          isPayload={isNotEmptyField(injectResultOutput.inject_injector_contract?.injector_contract_payload?.payload_id)}
                          type={
                            injectResultOutput.inject_injector_contract?.injector_contract_payload?.payload_id
                              ? injectResultOutput.inject_injector_contract.injector_contract_payload?.payload_collector_type
                              || injectResultOutput.inject_injector_contract.injector_contract_payload?.payload_type
                              : injectResultOutput.inject_type
                          }
                          variant="list"
                        />
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
                                {header.value?.(injectResultOutput)}
                              </div>
                            ))}
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                );
              })
        }
        {!injects ? (<Empty message={t('No data available')} />) : null}
      </List>
    </>
  );
};

export default InjectResultList;
