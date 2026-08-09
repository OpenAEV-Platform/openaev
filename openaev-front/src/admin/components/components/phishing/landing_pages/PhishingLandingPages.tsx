import { PublicOutlined } from '@mui/icons-material';
import { Box, Checkbox, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useContext, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { bulkDeletePhishingLandingPages, searchPhishingLandingPages } from '../../../../../actions/phishing/phishing-action';
import { initSorting } from '../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../../components/i18n';
import PaginatedListLoader from '../../../../../components/PaginatedListLoader';
import { type PhishingLandingPage, type SearchPaginationInput } from '../../../../../utils/api-types';
import useEntityToggle from '../../../../../utils/hooks/useEntityToggle';
import { AbilityContext, Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import ToolBar from '../../../common/ToolBar';
import CreatePhishingLandingPage from './CreatePhishingLandingPage';
import PhishingLandingPagePopover from './PhishingLandingPagePopover';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  phishing_landing_page_name: { width: '30%' },
  phishing_landing_page_description: { width: '40%' },
  phishing_landing_page_updated_at: { width: '30%' },
};

const PhishingLandingPages = () => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const ability = useContext(AbilityContext);

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');
  const [searchId] = searchParams.getAll('id');

  const headers: Header[] = useMemo(() => [
    {
      field: 'phishing_landing_page_name',
      label: 'Name',
      isSortable: true,
      value: (landingPage: PhishingLandingPage) => landingPage.phishing_landing_page_name,
    },
    {
      field: 'phishing_landing_page_description',
      label: 'Description',
      isSortable: true,
      value: (landingPage: PhishingLandingPage) => landingPage.phishing_landing_page_description || '-',
    },
    {
      field: 'phishing_landing_page_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (landingPage: PhishingLandingPage) => nsdt(landingPage.phishing_landing_page_updated_at),
    },
  ], [nsdt]);

  const availableFilterNames = [
    'phishing_landing_page_name',
    'phishing_landing_page_description',
  ];

  const [landingPages, setLandingPages] = useState<PhishingLandingPage[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'phishing_landing_pages',
    buildSearchPagination({
      sorts: initSorting('phishing_landing_page_name'),
      textSearch: search,
    }),
  );

  const [loading, setLoading] = useState<boolean>(true);
  const searchLandingPagesToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchPhishingLandingPages(input).finally(() => setLoading(false));
  };

  // Bulk selection
  const canDelete = ability.can(ACTIONS.DELETE, SUBJECTS.PHISHING);
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<PhishingLandingPage>(
    'phishing_landing_page',
    landingPages,
    queryableHelpers.paginationHelpers.getTotalElements(),
  );

  const bulkDelete = () => {
    bulkDeletePhishingLandingPages({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      landing_page_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      landing_page_ids_to_ignore: Object.keys(deSelectedElements),
    }).then((result) => {
      const deletedIds: string[] = result.data ?? [];
      const newTotal = Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - deletedIds.length);
      setLandingPages(landingPages.filter(landingPage => !deletedIds.includes(landingPage.phishing_landing_page_id)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newTotal);
      handleClearSelectedElements();
    });
  };

  return (
    <>
      <PaginationComponentV2
        fetch={searchLandingPagesToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setLandingPages}
        entityPrefix="phishing_landing_page"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.PHISHING}>
              <CreatePhishingLandingPage onCreate={result => setLandingPages([result, ...landingPages])} />
            </Can>
          </Box>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          sx={numberOfSelectedElements > 0
            ? {
                backgroundColor: 'background.accent',
                paddingBlock: 0.5,
              }
            : { paddingTop: 0 }}
          {...(numberOfSelectedElements === 0 ? { secondaryAction: <>&nbsp;</> } : {})}
        >
          {canDelete && (
            <ListItemIcon style={{ minWidth: 40 }}>
              <Checkbox
                edge="start"
                checked={selectAll}
                disableRipple
                onChange={handleToggleSelectAll}
              />
            </ListItemIcon>
          )}
          {numberOfSelectedElements > 0 ? (
            <ListItemText
              primary={(
                <ToolBar
                  numberOfSelectedElements={numberOfSelectedElements}
                  handleClearSelectedElements={handleClearSelectedElements}
                  handleBulkDelete={bulkDelete}
                  canManage={canDelete}
                  deleteConfirmationSingular={t('Do you want to delete this phishing landing page?')}
                  deleteConfirmationPlural={t('Do you want to delete these {count} phishing landing pages?', { count: String(numberOfSelectedElements) })}
                />
              )}
            />
          ) : (
            <>
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
            </>
          )}
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={PublicOutlined} headers={headers} headerStyles={inlineStyles} withCheckbox={canDelete} />
          : landingPages.map((landingPage: PhishingLandingPage) => (
              <ListItem
                key={landingPage.phishing_landing_page_id}
                divider
                disablePadding
                secondaryAction={(
                  <PhishingLandingPagePopover
                    landingPage={landingPage}
                    inList
                    openEditOnInit={landingPage.phishing_landing_page_id === searchId}
                    onUpdate={result => setLandingPages(landingPages.map(v => (v.phishing_landing_page_id !== result.phishing_landing_page_id ? v : result)))}
                    onDelete={result => setLandingPages(landingPages.filter(v => v.phishing_landing_page_id !== result))}
                  />
                )}
              >
                <ListItemButton
                  classes={{ root: classes.item }}
                  component={Link}
                  to={`/admin/components/phishing/landing_pages/${landingPage.phishing_landing_page_id}`}
                >
                  {canDelete && (
                    <ListItemIcon
                      style={{ minWidth: 40 }}
                      onClick={event => onToggleEntity(landingPage, event)}
                    >
                      <Checkbox
                        edge="start"
                        checked={
                          (selectAll && !(landingPage.phishing_landing_page_id in (deSelectedElements || {})))
                          || landingPage.phishing_landing_page_id in (selectedElements || {})
                        }
                        disableRipple
                      />
                    </ListItemIcon>
                  )}
                  <ListItemIcon>
                    <PublicOutlined color="primary" />
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
                            {header.value?.(landingPage)}
                          </div>
                        ))}
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            ))}
      </List>
    </>
  );
};

export default PhishingLandingPages;
