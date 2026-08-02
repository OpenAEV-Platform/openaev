import { MailOutlineOutlined } from '@mui/icons-material';
import { List, ListItemButton, ListItemIcon, ListItemSecondaryAction, ListItemText } from '@mui/material';
import { type CSSProperties } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchPhishingEmailTemplates } from '../../../../../actions/phishing/phishing-action';
import { type PhishingEmailTemplatesHelper } from '../../../../../actions/phishing/phishing-helper';
import Breadcrumbs from '../../../../../components/Breadcrumbs';
import useBodyItemsStyles from '../../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../../components/i18n';
import SearchFilter from '../../../../../components/SearchFilter';
import { useHelper } from '../../../../../store';
import { type PhishingEmailTemplate } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import useSearchAndFilter from '../../../../../utils/SortingFiltering';
import CreatePhishingEmailTemplate from './CreatePhishingEmailTemplate';
import PhishingEmailTemplatePopover from './PhishingEmailTemplatePopover';

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
  phishing_email_template_name: {
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
  },
  phishing_email_template_subject: {
    fontSize: 12,
    fontWeight: '700',
  },
};

const inlineStyles: Record<string, CSSProperties> = {
  phishing_email_template_name: {
    width: '30%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  phishing_email_template_subject: {
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

const PhishingEmailTemplates = () => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const searchColumns = ['name', 'subject'];
  const filtering = useSearchAndFilter('phishing_email_template', 'name', searchColumns);
  const { emailTemplates }: { emailTemplates: PhishingEmailTemplate[] } = useHelper(
    (helper: PhishingEmailTemplatesHelper) => ({ emailTemplates: helper.getPhishingEmailTemplates() }),
  );
  useDataLoader(() => {
    dispatch(fetchPhishingEmailTemplates());
  });
  const sorted: PhishingEmailTemplate[] = filtering.filterAndSort(emailTemplates);
  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Components') }, {
          label: t('Phishing emails'),
          current: true,
        }]}
      />
      <div className={classes.parameters}>
        <div className={classes.filters}>
          <SearchFilter variant="small" onChange={filtering.handleSearch} keyword={filtering.keyword} />
        </div>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PHISHING}>
          <CreatePhishingEmailTemplate />
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
                {filtering.buildHeader('phishing_email_template_name', 'Name', true, headerStyles)}
                {filtering.buildHeader('phishing_email_template_subject', 'Subject', true, headerStyles)}
              </div>
            )}
          />
          <ListItemSecondaryAction>&nbsp;</ListItemSecondaryAction>
        </ListItemButton>
        {sorted.map(emailTemplate => (
          <ListItemButton
            key={emailTemplate.phishing_email_template_id}
            classes={{ root: classes.item }}
            divider
            component={Link}
            to={`/admin/components/phishing/email_templates/${emailTemplate.phishing_email_template_id}`}
          >
            <ListItemIcon>
              <MailOutlineOutlined color="primary" />
            </ListItemIcon>
            <ListItemText
              primary={(
                <div style={bodyItemsStyles.bodyItems}>
                  <div style={{
                    ...bodyItemsStyles.bodyItem,
                    ...inlineStyles.phishing_email_template_name,
                  }}
                  >
                    {emailTemplate.phishing_email_template_name}
                  </div>
                  <div style={{
                    ...bodyItemsStyles.bodyItem,
                    ...inlineStyles.phishing_email_template_subject,
                  }}
                  >
                    {emailTemplate.phishing_email_template_subject}
                  </div>
                </div>
              )}
            />
            <ListItemSecondaryAction>
              <PhishingEmailTemplatePopover emailTemplate={emailTemplate} inList />
            </ListItemSecondaryAction>
          </ListItemButton>
        ))}
      </List>
    </>
  );
};

export default PhishingEmailTemplates;
