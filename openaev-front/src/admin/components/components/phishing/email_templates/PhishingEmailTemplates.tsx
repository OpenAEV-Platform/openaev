import { MailOutlineOutlined } from '@mui/icons-material';
import { Box, Checkbox, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { bulkDeletePhishingEmailTemplates, searchPhishingEmailTemplates } from '../../../../../actions/phishing/phishing-action';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import { initSorting } from '../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../../components/i18n';
import PaginatedListLoader from '../../../../../components/PaginatedListLoader';
import { type PhishingEmailTemplate, type SearchPaginationInput } from '../../../../../utils/api-types';
import useEntityToggle from '../../../../../utils/hooks/useEntityToggle';
import { Can, useAbility } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import ToolBar from '../../../common/ToolBar';
import PhishingEmailTemplatePopover from './PhishingEmailTemplatePopover';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  phishing_email_template_name: { width: '25%' },
  phishing_email_template_subject: { width: '25%' },
  phishing_email_template_from_email: { width: '20%' },
  phishing_email_template_updated_at: { width: '30%' },
};

const PhishingEmailTemplates = () => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const navigate = useNavigate();
  const ability = useAbility();

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');
  const [searchId] = searchParams.getAll('id');

  const headers: Header[] = useMemo(() => [
    {
      field: 'phishing_email_template_name',
      label: 'Name',
      isSortable: true,
      value: (emailTemplate: PhishingEmailTemplate) => emailTemplate.phishing_email_template_name,
    },
    {
      field: 'phishing_email_template_subject',
      label: 'Subject',
      isSortable: true,
      value: (emailTemplate: PhishingEmailTemplate) => emailTemplate.phishing_email_template_subject,
    },
    {
      field: 'phishing_email_template_from_email',
      label: 'From',
      isSortable: true,
      value: (emailTemplate: PhishingEmailTemplate) => emailTemplate.phishing_email_template_from_email || '-',
    },
    {
      field: 'phishing_email_template_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (emailTemplate: PhishingEmailTemplate) => nsdt(emailTemplate.phishing_email_template_updated_at),
    },
  ], [nsdt]);

  const availableFilterNames = [
    'phishing_email_template_name',
    'phishing_email_template_subject',
    'phishing_email_template_from_name',
    'phishing_email_template_from_email',
  ];

  const [emailTemplates, setEmailTemplates] = useState<PhishingEmailTemplate[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'phishing_email_templates',
    buildSearchPagination({
      sorts: initSorting('phishing_email_template_name'),
      textSearch: search,
    }),
  );

  const [loading, setLoading] = useState<boolean>(true);
  const searchEmailTemplatesToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchPhishingEmailTemplates(input).finally(() => setLoading(false));
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
  } = useEntityToggle<PhishingEmailTemplate>(
    'phishing_email_template',
    emailTemplates,
    queryableHelpers.paginationHelpers.getTotalElements(),
  );

  const bulkDelete = () => {
    bulkDeletePhishingEmailTemplates({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      email_template_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      email_template_ids_to_ignore: Object.keys(deSelectedElements),
    }).then((result) => {
      const deletedIds: string[] = result.data ?? [];
      const newTotal = Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - deletedIds.length);
      setEmailTemplates(emailTemplates.filter(emailTemplate => !deletedIds.includes(emailTemplate.phishing_email_template_id)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newTotal);
      handleClearSelectedElements();
    });
  };

  return (
    <>
      <PaginationComponentV2
        fetch={searchEmailTemplatesToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setEmailTemplates}
        entityPrefix="phishing_email_template"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.PHISHING}>
              <ButtonCreate onClick={() => navigate('/admin/components/phishing/email_templates/create')} />
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
                  deleteConfirmationSingular={t('Do you want to delete this phishing email template?')}
                  deleteConfirmationPlural={t('Do you want to delete these {count} phishing email templates?', { count: String(numberOfSelectedElements) })}
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
          ? <PaginatedListLoader Icon={MailOutlineOutlined} headers={headers} headerStyles={inlineStyles} withCheckbox={canDelete} />
          : emailTemplates.map((emailTemplate: PhishingEmailTemplate) => (
              <ListItem
                key={emailTemplate.phishing_email_template_id}
                divider
                disablePadding
                secondaryAction={(
                  <PhishingEmailTemplatePopover
                    emailTemplate={emailTemplate}
                    inList
                    openEditOnInit={emailTemplate.phishing_email_template_id === searchId}
                    onDelete={result => setEmailTemplates(emailTemplates.filter(v => v.phishing_email_template_id !== result))}
                  />
                )}
              >
                <ListItemButton
                  classes={{ root: classes.item }}
                  component={Link}
                  to={`/admin/components/phishing/email_templates/${emailTemplate.phishing_email_template_id}`}
                >
                  {canDelete && (
                    <ListItemIcon
                      style={{ minWidth: 40 }}
                      onClick={event => onToggleEntity(emailTemplate, event)}
                    >
                      <Checkbox
                        edge="start"
                        checked={
                          (selectAll && !(emailTemplate.phishing_email_template_id in (deSelectedElements || {})))
                          || emailTemplate.phishing_email_template_id in (selectedElements || {})
                        }
                        disableRipple
                      />
                    </ListItemIcon>
                  )}
                  <ListItemIcon>
                    <MailOutlineOutlined color="primary" />
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
                            {header.value?.(emailTemplate)}
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

export default PhishingEmailTemplates;
