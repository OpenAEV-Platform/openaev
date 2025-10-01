import { KeyboardArrowRight } from '@mui/icons-material';
import { type TabPanelProps } from '@mui/lab';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tab, Tabs } from '@mui/material';
import { type CSSProperties, type SyntheticEvent, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fullTextSearch, fullTextSearchByClass } from '../../../actions/fullTextSearch-action';
import Breadcrumbs from '../../../components/Breadcrumbs';
import PaginationComponent from '../../../components/common/pagination/PaginationComponent';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { type Header } from '../../../components/common/SortHeadersList';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import { type FullTextSearchCountResult, type FullTextSearchResult, type SearchPaginationInput } from '../../../utils/api-types';
import useEntityIcon from '../../../utils/hooks/useEntityIcon';
import useEntityLink from './useEntityLink';

const useStyles = makeStyles()(theme => ({
  container: { display: 'flex' },
  tabs: { minWidth: 'fit-content' },
  tab: {
    whiteSpace: 'nowrap',
    minWidth: 'fit-content',
  },
  itemHead: { textTransform: 'uppercase' },
  bodyItemHeader: {
    fontSize: theme.typography.h4.fontSize,
    fontWeight: 700,
  },
  item: { height: 50 },
  bodyItem: {
    fontSize: theme.typography.h3.fontSize,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  goIcon: { justifyContent: 'right' },
}));

const inlineStyles: Record<string, CSSProperties> = {
  result_name: { width: '40%' },
  result_description: { width: '40%' },
  result_tags: { width: '20%' },
};

const TabPanel = (props: TabPanelProps & {
  index: number;
  entity: string;
  searchPaginationInput: SearchPaginationInput;
}) => {
  const { value, index, entity, searchPaginationInput } = props;

  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();

  // Headers
  const fields: Header[] = [
    {
      field: 'result_name',
      label: 'Name',
      isSortable: false,
      value: (result: FullTextSearchResult) => <span>{result.name}</span>,
    },
    {
      field: 'result_description',
      label: 'Description',
      isSortable: false,
      value: (result: FullTextSearchResult) => <span>{result.description}</span>,
    },
    {
      field: 'result_tags',
      label: 'Tags',
      isSortable: false,
      value: (result: FullTextSearchResult) => <span><ItemTags variant="list" tags={result.tags} /></span>,
    },
  ];

  const [elements, setElements] = useState<FullTextSearchResult[]>([]);

  return (
    <div
      role="tabpanel"
      hidden={value !== index.toString()}
      aria-labelledby={`vertical-tab-${index}`}
      style={{
        width: '100%',
        padding: '0 24px',
      }}
    >
      {value === index.toString() && (
        <>
          <PaginationComponent
            fetch={input => fullTextSearchByClass(entity, {
              ...input,
              ...searchPaginationInput,
            })}
            searchPaginationInput={searchPaginationInput}
            setContent={setElements}
            searchEnable={false}
          />
          <List>
            <ListItem
              classes={{ root: classes.itemHead }}
              divider={false}
              style={{ paddingTop: 0 }}
            >
              <ListItemIcon />
              <ListItemText
                primary={(
                  <div className={classes.container}>
                    {fields.map(header => (
                      <div
                        key={header.field}
                        className={classes.bodyItemHeader}
                        style={inlineStyles[header.field]}
                      >
                        <span>{t(header.label)}</span>
                      </div>
                    ))}
                  </div>
                )}
              />
              <ListItemIcon />
            </ListItem>
            {elements.map(result => (
              <ListItemButton
                key={result.id}
                classes={{ root: classes.item }}
                divider
                component={Link}
                to={useEntityLink(result.clazz, result.id, searchPaginationInput.textSearch ?? '')}
              >
                <ListItemIcon>
                  {useEntityIcon(result.clazz)}
                </ListItemIcon>
                <ListItemText
                  primary={(
                    <div className={classes.container}>
                      {fields.map(field => (
                        <div
                          key={field.field}
                          className={classes.bodyItem}
                          style={inlineStyles[field.field]}
                        >
                          {field.value?.(result)}
                        </div>
                      ))}
                    </div>
                  )}
                />
                <ListItemIcon classes={{ root: classes.goIcon }}>
                  <KeyboardArrowRight />
                </ListItemIcon>
              </ListItemButton>
            ))}
          </List>
        </>
      )}
    </div>
  );
};

const FullTextSearch = () => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();

  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');
  const [searchPaginationInput, setSearchPaginationInput] = useState(buildSearchPagination({ textSearch: search }));

  const [results, setResults] = useState<Record<string, FullTextSearchCountResult>>({});

  useEffect(() => {
    fullTextSearch(search).then((result: { data: Record<string, FullTextSearchCountResult> }) => {
      setResults(result.data);
    });
    setSearchPaginationInput(buildSearchPagination({ textSearch: search }));
  }, [search]);

  // Tabs
  const [value, setValue] = useState(0);

  const handleChange = (_event: SyntheticEvent, newValue: number) => {
    setValue(newValue);
  };

  // Utils
  const entries = (r: Record<string, FullTextSearchCountResult>) => {
    return Object.entries(r).sort((e1, e2) => e1[0].localeCompare(e2[0]));
  };

  return (
    <>
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Search'),
            current: true,
          },
        ]}
      />
      <Box className={classes.container}>
        <Tabs
          orientation="vertical"
          variant="scrollable"
          value={value}
          onChange={handleChange}
          aria-label="Vertical tabs example"
          className={classes.tabs}
        >
          {entries(results).map(([entity, result]) => (
            <Tab
              key={entity}
              label={`${t(result.clazz)} (${result.count})`}
              className={classes.tab}
            />
          ))}
        </Tabs>
        {entries(results).map(([entity], idx) => (
          <TabPanel
            key={entity}
            value={value.toString()}
            index={idx}
            entity={entity}
            searchPaginationInput={searchPaginationInput}
          />
        ))}
      </Box>
    </>
  );
};

export default FullTextSearch;
