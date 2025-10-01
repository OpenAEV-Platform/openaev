import { ChevronRightOutlined, HelpOutlineOutlined, SchoolOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemSecondaryAction, ListItemText } from '@mui/material';
import { type CSSProperties, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchLessonsTemplates } from '../../../../actions/Lessons';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponent from '../../../../components/common/pagination/PaginationComponent.js';
import SortHeadersComponent from '../../../../components/common/pagination/SortHeadersComponent';
import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type LessonsTemplate, type SearchPaginationInput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CreateLessonsTemplate from './CreateLessonsTemplate';

const useStyles = makeStyles()(() => ({
  itemHead: {
    paddingLeft: 17,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  lessons_template_name: { width: '25%' },
  lessons_template_description: { width: '50%' },
};

const LessonsTemplates = () => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  // Headers
  const headers = [
    {
      field: 'lessons_template_name',
      label: 'Name',
      isSortable: true,
      value: (lessonsTemplate: LessonsTemplate) => lessonsTemplate.lessons_template_name,
    },
    {
      field: 'lessons_template_description',
      label: 'Description',
      isSortable: true,
      value: (lessonsTemplate: LessonsTemplate) => lessonsTemplate.lessons_template_description,
    },
  ];

  const [lessonTemplates, setLessonTemplates] = useState<LessonsTemplate[]>([]);
  const [searchPaginationInput, setSearchPaginationInput] = useState<SearchPaginationInput>(buildSearchPagination({ sorts: initSorting('lessons_template_name') }));

  const [loading, setLoading] = useState<boolean>(true);
  const searchLessonsTemplatesToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchLessonsTemplates(input).finally(() => setLoading(false));
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Components') }, {
          label: t('Lessons learned'),
          current: true,
        }]}
      />
      <PaginationComponent
        fetch={searchLessonsTemplatesToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setLessonTemplates}
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
              <SortHeadersComponent
                headers={headers}
                inlineStylesHeaders={inlineStyles}
                searchPaginationInput={searchPaginationInput}
                setSearchPaginationInput={setSearchPaginationInput}
              />
            )}
          />
          <ListItemSecondaryAction />
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
          : lessonTemplates.map((lessonsTemplate) => {
              return (
                <ListItemButton
                  key={lessonsTemplate.lessonstemplate_id}
                  classes={{ root: classes.item }}
                  divider
                  component={Link}
                  to={`/admin/components/lessons/${lessonsTemplate.lessonstemplate_id}`}
                >
                  <ListItemIcon>
                    <SchoolOutlined color="primary" />
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
                            {header.value(lessonsTemplate)}
                          </div>
                        ))}
                      </div>
                    )}
                  />
                  <ListItemSecondaryAction>
                    <ChevronRightOutlined />
                  </ListItemSecondaryAction>
                </ListItemButton>
              );
            })}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
        <CreateLessonsTemplate onCreate={result => setLessonTemplates([result, ...lessonTemplates])} />
      </Can>
    </>
  );
};

export default LessonsTemplates;
