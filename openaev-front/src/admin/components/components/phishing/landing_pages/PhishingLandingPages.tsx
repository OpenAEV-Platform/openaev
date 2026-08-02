import { PublicOutlined } from '@mui/icons-material';
import { List, ListItemButton, ListItemIcon, ListItemSecondaryAction, ListItemText } from '@mui/material';
import { type CSSProperties } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchPhishingLandingPages } from '../../../../../actions/phishing/phishing-action';
import { type PhishingLandingPagesHelper } from '../../../../../actions/phishing/phishing-helper';
import Breadcrumbs from '../../../../../components/Breadcrumbs';
import useBodyItemsStyles from '../../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../../components/i18n';
import SearchFilter from '../../../../../components/SearchFilter';
import { useHelper } from '../../../../../store';
import { type PhishingLandingPage } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import useSearchAndFilter from '../../../../../utils/SortingFiltering';
import CreatePhishingLandingPage from './CreatePhishingLandingPage';
import PhishingLandingPagePopover from './PhishingLandingPagePopover';

const useStyles = makeStyles()(() => ({
  parameters: {
    marginTop: -10,
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    height: 52,
  },
  filters: {
    display: 'flex',
    gap: '10px',
  },
  itemHead: {
    textTransform: 'uppercase',
    cursor: 'pointer',
    height: 40,
  },
  item: { height: 50 },
}));

const headerStyles: Record<string, CSSProperties> = {
  phishing_landing_page_name: {
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
  },
  phishing_landing_page_description: {
    fontSize: 12,
    fontWeight: '700',
  },
};

const inlineStyles: Record<string, CSSProperties> = {
  phishing_landing_page_name: {
    width: '30%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  phishing_landing_page_description: {
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

const PhishingLandingPages = () => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const searchColumns = ['name', 'description'];
  const filtering = useSearchAndFilter('phishing_landing_page', 'name', searchColumns);
  const { landingPages }: { landingPages: PhishingLandingPage[] } = useHelper(
    (helper: PhishingLandingPagesHelper) => ({ landingPages: helper.getPhishingLandingPages() }),
  );
  useDataLoader(() => {
    dispatch(fetchPhishingLandingPages());
  });
  const sorted: PhishingLandingPage[] = filtering.filterAndSort(landingPages);
  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Components') }, {
          label: t('Phishing pages'),
          current: true,
        }]}
      />
      <div className={classes.parameters}>
        <div className={classes.filters}>
          <SearchFilter variant="small" onChange={filtering.handleSearch} keyword={filtering.keyword} />
        </div>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PHISHING}>
          <CreatePhishingLandingPage />
        </Can>
      </div>
      <div className="clearfix" />
      <List>
        <ListItemButton
          classes={{ root: classes.itemHead }}
          component="div"
          disableRipple
          divider={false}
          sx={{
            'cursor': 'pointer',
            '&:hover': { backgroundColor: 'transparent' },
          }}
        >
          <ListItemIcon>
            <span style={{
              padding: '0 8px 0 8px',
              fontWeight: 700,
              fontSize: 12,
            }}
            >
              &nbsp;
            </span>
          </ListItemIcon>
          <ListItemText
            primary={(
              <div style={bodyItemsStyles.bodyItems}>
                {filtering.buildHeader('phishing_landing_page_name', 'Name', true, headerStyles)}
                {filtering.buildHeader('phishing_landing_page_description', 'Description', true, headerStyles)}
              </div>
            )}
          />
          <ListItemSecondaryAction>&nbsp;</ListItemSecondaryAction>
        </ListItemButton>
        {sorted.map(landingPage => (
          <ListItemButton
            key={landingPage.phishing_landing_page_id}
            classes={{ root: classes.item }}
            divider
            component={Link}
            to={`/admin/components/phishing/landing_pages/${landingPage.phishing_landing_page_id}`}
          >
            <ListItemIcon>
              <PublicOutlined color="primary" />
            </ListItemIcon>
            <ListItemText
              primary={(
                <div style={bodyItemsStyles.bodyItems}>
                  <div style={{
                    ...bodyItemsStyles.bodyItem,
                    ...inlineStyles.phishing_landing_page_name,
                  }}
                  >
                    {landingPage.phishing_landing_page_name}
                  </div>
                  <div style={{
                    ...bodyItemsStyles.bodyItem,
                    ...inlineStyles.phishing_landing_page_description,
                  }}
                  >
                    {landingPage.phishing_landing_page_description}
                  </div>
                </div>
              )}
            />
            <ListItemSecondaryAction>
              <PhishingLandingPagePopover landingPage={landingPage} inList />
            </ListItemSecondaryAction>
          </ListItemButton>
        ))}
      </List>
    </>
  );
};

export default PhishingLandingPages;
