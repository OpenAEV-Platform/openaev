import { ForwardToInbox, HelpOutlineOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText, useTheme } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import PaginationComponent from '../../../components/common/pagination/PaginationComponent';
import SortHeadersComponent from '../../../components/common/pagination/SortHeadersComponent';
import { type Page } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import ItemStatus from '../../../components/ItemStatus';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { type InjectTestStatusOutput, type SearchPaginationInput } from '../../../utils/api-types';
import { InjectTestContext, PermissionsContext } from '../common/Context';
import InjectIcon from '../common/injects/InjectIcon';
import InjectTestDetail from './InjectTestDetail';
import InjectTestPopover from './InjectTestPopover';
import InjectTestReplayAll from './InjectTestReplayAll';

const useStyles = makeStyles()(() => ({
  bodyItems: {
    display: 'flex',
    alignItems: 'center',
  },
  bodyItem: {
    minHeight: 20,
    fontSize: 13,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    paddingRight: 10,
  },
  itemHead: {
    paddingLeft: 10,
    marginBottom: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  inject_title: {
    width: '40%',
    cursor: 'default',
  },
  tracking_sent_date: { width: '40%' },
  status_name: { width: '20%' },
};

interface Props { statusId: string | undefined }

const InjectTestList: FunctionComponent<Props> = ({ statusId }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);

  const [selectedInjectTestStatus, setSelectedInjectTestStatus] = useState<InjectTestStatusOutput | null>(null);

  const {
    contextId,
    searchInjectTests,
    fetchInjectTestStatus,
  } = useContext(InjectTestContext);

  // Fetching test
  useEffect(() => {
    if (statusId !== null && statusId !== undefined && fetchInjectTestStatus) {
      fetchInjectTestStatus(statusId).then((result: { data: InjectTestStatusOutput }) => {
        setSelectedInjectTestStatus(result.data);
      });
    }
  }, [statusId]);

  // Headers
  const headers = [
    {
      field: 'inject_title',
      label: 'Inject title',
      isSortable: true,
      value: (test: InjectTestStatusOutput) => test.inject_title,
    },
    {
      field: 'tracking_sent_date',
      label: 'Test execution time',
      isSortable: true,
      value: (test: InjectTestStatusOutput) => fldt(test.tracking_sent_date),
    },
    {
      field: 'status_name',
      label: 'Test status',
      isSortable: true,
      value: (test: InjectTestStatusOutput) => {
        return (
          <ItemStatus
            isInject
            status={test.status_name}
            label={t(test.status_name || '-')}
            variant="inList"
          />
        );
      },
    },
  ];

  // Filter and sort hook
  // null until the first page is loaded, so the empty state does not flash before it
  const [tests, setTests] = useState<InjectTestStatusOutput[] | null>(null);
  const [searchPaginationInput, setSearchPaginationInput] = useState<SearchPaginationInput>(buildSearchPagination({}));
  // Whether the context holds any test at all, whatever the text search: null until known.
  // Without tests, the search bar, pagination and column headers give way to the empty state.
  const [hasTests, setHasTests] = useState<boolean | null>(null);

  const [loading, setLoading] = useState<boolean>(true);
  const searchInjectTestsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    try {
      if (searchInjectTests) {
        return searchInjectTests(contextId, input).then((result) => {
          // A search without match keeps the search bar, so it can still be cleared
          if (!input.textSearch) {
            setHasTests(result.data.totalElements > 0);
          }
          return result;
        });
      } else {
        return new Promise<{ data: Page<InjectTestStatusOutput> }>(() => {
        });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      {/* Kept mounted while hidden: it is the one fetching the tests */}
      <div hidden={!hasTests}>
        <PaginationComponent
          fetch={searchInjectTestsToLoad}
          searchPaginationInput={searchPaginationInput}
          setContent={setTests}
        >
          <InjectTestReplayAll
            searchPaginationInput={searchPaginationInput}
            injectIds={tests?.map((test: InjectTestStatusOutput) => test.inject_id!) ?? []}
            onTest={result => setTests(result)}
          />
        </PaginationComponent>
      </div>
      <List style={{ marginTop: theme.spacing(2) }} disablePadding>
        {hasTests && (
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            style={{ paddingTop: 0 }}
            secondaryAction={<>&nbsp;</>}
          >
            <ListItemIcon />
            <ListItemText
              primary={(
                <SortHeadersComponent
                  headers={headers}
                  inlineStylesHeaders={inlineStyles}
                  searchPaginationInput={searchPaginationInput}
                  setSearchPaginationInput={setSearchPaginationInput}
                  defaultSortAsc
                />
              )}
            />
          </ListItem>
        )}
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
          : tests?.map((test) => {
              return (
                <ListItem
                  key={test.status_id}
                  divider
                  secondaryAction={(
                    permissions.canManage && (
                      <InjectTestPopover
                        injectTest={test}
                        onTest={result =>
                          setTests(tests?.map(existing => existing.status_id !== result.status_id ? existing : result))}
                        onDelete={(injectStatusId) => {
                          setTests(tests.filter(existing => (existing.status_id !== injectStatusId)));
                          // Refetch so the counter and the empty state follow, e.g. once the last test is gone
                          setSearchPaginationInput(prev => ({ ...prev }));
                        }}
                      />
                    )

                  )}
                  disablePadding
                >
                  <ListItemButton
                    classes={{ root: classes.item }}
                    onClick={() => setSelectedInjectTestStatus(test)}
                    selected={test.status_id === selectedInjectTestStatus?.status_id}
                  >
                    <ListItemIcon>
                      <InjectIcon
                        type={test.inject_type}
                        variant="list"
                      />
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div className={classes.bodyItems}>
                          {headers.map(header => (
                            <div
                              key={header.field}
                              className={classes.bodyItem}
                              style={inlineStyles[header.field]}
                            >
                              {header.value(test)}
                            </div>
                          ))}
                        </div>
                      )}
                    />
                  </ListItemButton>
                </ListItem>
              );
            })}
        {hasTests === false && (
          <Empty
            message={t('No test yet')}
            hint={t('Test inject from the Injects tab to see its result here.')}
            icon={ForwardToInbox}
          />
        )}
        {hasTests && tests?.length === 0 && (<Empty message={t('No data available')} />)}
      </List>
      {
        selectedInjectTestStatus !== null
        && (
          <InjectTestDetail
            open
            handleClose={() => setSelectedInjectTestStatus(null)}
            injectTestStatus={selectedInjectTestStatus}
          />
        )
      }
    </>
  );
};

export default InjectTestList;
