import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { type CSSProperties, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { searchTagRules } from '../../../../actions/tag_rules/tagrule-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import ItemTargets from '../../../../components/ItemTargets';
import { type TagRuleOutput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import TagRuleCreate from './TagRuleCreate';
import TagRulePopover from './TagRulePopover';

const useStyles = makeStyles()(() => ({ itemHead: { textTransform: 'uppercase' } }));

const inlineStyles: Record<string, CSSProperties> = {
  tag_rule_tag: { width: '20%' },
  tag_rule_asset_groups: { width: '70%' },
};

const TagRules = () => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const [tagRules, setTagRules] = useState<TagRuleOutput[]>([]);

  // Filter
  const availableFilterNames = [
    'tag_rule_tag',
    'tag_rule_asset_groups',
  ];
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('tag-rules2', buildSearchPagination({}));

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'tag_rule_tag',
      label: 'Tag',
      isSortable: true,
      value: (tagRule: TagRuleOutput) => tagRule.tag_name,
    },
    {
      field: 'tag_rule_asset_groups',
      label: 'Asset Groups',
      isSortable: true,
      value: (tagRule: TagRuleOutput) => (
        <ItemTargets targets={Object.entries(tagRule.asset_groups ?? []).map(([target_id, target_name]) => ({
          target_id,
          target_name,
          target_type: 'ASSETS_GROUPS',
        }),
        )}
        />
      ),
    },
  ], []);

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Settings') }, { label: t('Customization') }, {
          label: t('Default asset rules'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={searchTagRules}
        searchPaginationInput={searchPaginationInput}
        setContent={setTagRules}
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        entityPrefix="tag_rule"
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
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
        {tagRules.map((tagRule: TagRuleOutput) => (

          <ListItem
            key={tagRule.tag_rule_id}
            secondaryAction={(
              <TagRulePopover
                tagRule={tagRule}
                onDelete={result => setTagRules(tagRules.filter(ag => (ag.tag_rule_id !== result)))}
                onUpdate={result => setTagRules(tagRules.map(existing => (existing.tag_rule_id !== result.tag_rule_id ? existing : result)))}
              />
            )}
            divider
          >
            <ListItemIcon>
              <SelectGroup color="primary" />
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
                      {header.value?.(tagRule)}
                    </div>
                  ))}
                </div>
              )}
            />
          </ListItem>
        ))}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
        <TagRuleCreate
          onCreate={result => setTagRules([...tagRules, result])}
        />
      </Can>
    </>
  );
};

export default TagRules;
