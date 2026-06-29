import { HelpOutlineOutlined, SmartToyOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText } from '@mui/material';
import { type CSSProperties, useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchAiTargets } from '../../../../actions/assets/aiTarget-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponent from '../../../../components/common/pagination/PaginationComponent';
import SortHeadersComponent from '../../../../components/common/pagination/SortHeadersComponent';
import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type AiTarget, type SearchPaginationInput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isNotEmptyField } from '../../../../utils/utils';
import AiTargetCreation from './AiTargetCreation';
import AiTargetPopover from './AiTargetPopover';

const useStyles = makeStyles()(() => ({
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
}));

const inlineStyles: Record<string, CSSProperties> = {
  asset_name: { width: '25%' },
  ai_target_provider: { width: '20%' },
  ai_target_model: { width: '20%' },
  ai_target_modality: { width: '15%' },
  asset_tags: { width: '20%' },
};

const AiTargets = () => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t } = useFormatter();

  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');
  const [searchId] = searchParams.getAll('id');

  const headers = [
    {
      field: 'asset_name',
      label: 'Name',
      isSortable: true,
    },
    {
      field: 'ai_target_provider',
      label: 'Provider',
      isSortable: true,
    },
    {
      field: 'ai_target_model',
      label: 'Model',
      isSortable: true,
    },
    {
      field: 'ai_target_modality',
      label: 'Modality',
      isSortable: true,
    },
    {
      field: 'asset_tags',
      label: 'Tags',
      isSortable: true,
    },
  ];

  const [aiTargets, setAiTargets] = useState<AiTarget[]>([]);
  const [searchPaginationInput, setSearchPaginationInput] = useState<SearchPaginationInput>(buildSearchPagination({
    sorts: initSorting('asset_name'),
    textSearch: search,
  }));

  const exportProps = {
    exportType: 'aiTarget',
    exportKeys: [
      'asset_name',
      'ai_target_provider',
      'ai_target_model',
      'ai_target_modality',
      'asset_tags',
    ],
    exportData: aiTargets,
    exportFileName: `${t('AI Targets')}.csv`,
  };

  const [loading, setLoading] = useState<boolean>(true);
  const searchAiTargetsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchAiTargets(input).finally(() => setLoading(false));
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Assets') }, {
          label: t('AI targets'),
          current: true,
        }]}
      />
      <PaginationComponent
        fetch={searchAiTargetsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setAiTargets}
        exportProps={exportProps}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
        >
          <ListItemIcon>
            <span
              style={{
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
              <SortHeadersComponent
                headers={headers}
                inlineStylesHeaders={inlineStyles}
                searchPaginationInput={searchPaginationInput}
                setSearchPaginationInput={setSearchPaginationInput}
              />
            )}
          />
          <ListItemSecondaryAction> &nbsp; </ListItemSecondaryAction>
        </ListItem>
        {loading
          ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
          : aiTargets.map((aiTarget: AiTarget) => {
              return (
                <ListItem
                  key={aiTarget.asset_id}
                  classes={{ root: classes.item }}
                  divider={true}
                >
                  <ListItemIcon>
                    <SmartToyOutlined color="primary" />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div style={bodyItemsStyles.bodyItems}>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.asset_name,
                        }}
                        >
                          {aiTarget.asset_name}
                        </div>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.ai_target_provider,
                        }}
                        >
                          {aiTarget.ai_target_provider}
                        </div>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.ai_target_model,
                        }}
                        >
                          {aiTarget.ai_target_model || '-'}
                        </div>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.ai_target_modality,
                        }}
                        >
                          {aiTarget.ai_target_modality}
                        </div>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.asset_tags,
                        }}
                        >
                          <ItemTags variant="list" tags={aiTarget.asset_tags} />
                        </div>
                      </div>
                    )}
                  />
                  <ListItemSecondaryAction>
                    <AiTargetPopover
                      aiTarget={{
                        ...aiTarget,
                        type: 'static',
                      }}
                      onUpdate={result => setAiTargets(aiTargets.map(e => (e.asset_id !== result.asset_id ? e : result)))}
                      onDelete={result => setAiTargets(aiTargets.filter(e => (e.asset_id !== result)))}
                      openEditOnInit={aiTarget.asset_id === searchId}
                      disabled={isNotEmptyField(aiTarget.asset_external_reference)}
                    />
                  </ListItemSecondaryAction>
                </ListItem>
              );
            })}
      </List>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSETS}>
        <AiTargetCreation onCreate={result => setAiTargets([result, ...aiTargets])} />
      </Can>
    </>
  );
};

export default AiTargets;
