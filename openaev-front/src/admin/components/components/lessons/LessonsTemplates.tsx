import { ChevronRightOutlined, HelpOutlineOutlined, SchoolOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemSecondaryAction, ListItemText } from '@mui/material';
import { type CSSProperties, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchLessonsTemplates } from '../../../../actions/Lessons';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponent from '../../../../components/common/pagination/PaginationComponent';
import SortHeadersComponent from '../../../../components/common/pagination/SortHeadersComponent';
import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { LESSONS_TEMPLATES_BASE_URL } from '../../../../constants/BaseUrls';
import { type LessonsTemplate, type SearchPaginationInput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import CustomizationMenu from '../../settings/CustomizationMenu';
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
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Customization') }, {
            label: t('Lessons learned'),
            current: true,
          }]}
        />
        <PaginationComponent
          fetch={searchLessonsTemplatesToLoad}
          searchPaginationInput={searchPaginationInput}
          setContent={setLessonTemplates}
          createButton={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.LESSONS_LEARNED}>
              <CreateLessonsTemplate onCreate={result => setLessonTemplates([result, ...lessonTemplates])} />
            </Can>
          )}
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
                    to={`${LESSONS_TEMPLATES_BASE_URL}/${lessonsTemplate.lessonstemplate_id}`}
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
                    {/* Flex wrapper: the svg otherwise sits on the text baseline, leaving
                        descent space below and pushing the icon a few px above center. */}
                    <ListItemSecondaryAction sx={{
                      display: 'flex',
                      alignItems: 'center',
                    }}
                    >
                      <ChevronRightOutlined />
                    </ListItemSecondaryAction>
                  </ListItemButton>
                );
              })}
        </List>
      </div>
      <CustomizationMenu />
    </div>
  );
};

export default LessonsTemplates;
