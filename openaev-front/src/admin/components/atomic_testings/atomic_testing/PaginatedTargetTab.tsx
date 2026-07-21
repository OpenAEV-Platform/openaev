import { Box, List } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';

import { searchTargets } from '../../../../actions/injects/inject-action';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import { type InjectTarget } from '../../../../utils/api-types';
import NewTargetListItem from './NewTargetListItem';

interface Props {
  handleSelectTarget: (target: InjectTarget) => void;
  entityPrefix: string;
  inject_id: string;
  target_type: string;
  reloadContentCount: number;
  selectedTargetId?: string;
  onTargetsChange?: (targets: InjectTarget[]) => void;
}

const PaginatedTargetTab: React.FC<Props> = (props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const pagination = useQueryableWithLocalStorage(props.target_type + '_' + props.inject_id + '_filters', buildSearchPagination({
    filterGroup: {
      mode: 'and',
      filters: [],
    },
  }));

  const [targets, setTargets] = useState<InjectTarget[]>();
  const [searchReloadContentCount, setSearchReloadContentCount] = useState(0);

  const { onTargetsChange } = props;

  useEffect(() => {
    setSearchReloadContentCount(searchReloadContentCount + 1);
  }, [props.reloadContentCount]);

  const handleSetTargets = (content: InjectTarget[]) => {
    setTargets(content);
    onTargetsChange?.(content);
  };

  // Selection is owned by the parent (AtomicTesting) so the results header and
  // its prev/next switcher stay in sync with the highlighted row. We only
  // auto-select the first target when the current selection is no longer on the
  // loaded page (e.g. after switching tabs or paginating).
  useEffect(() => {
    if (targets && targets.length > 0 && !targets.find(t => t.target_id === props.selectedTargetId)) {
      props.handleSelectTarget(targets[0]);
    }
  }, [targets, props.selectedTargetId]);

  return (
    <>
      <PaginationComponentV2
        fetch={input => searchTargets(props.inject_id, props.target_type, input)}
        searchPaginationInput={pagination.searchPaginationInput}
        setContent={handleSetTargets}
        entityPrefix={props.entityPrefix}
        queryableHelpers={pagination.queryableHelpers}
        reloadContentCount={searchReloadContentCount}
        contextId={props.inject_id}
      />
      {targets && targets.length > 0 ? (
        <Box
          sx={{
            'marginTop': 1,
            'border': `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
            'borderRadius': 1,
            'overflow': 'hidden',
            '& > .MuiList-root > *:not(:first-of-type)': { borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}` },
          }}
        >
          <List disablePadding>
            {targets.map(target => (
              <NewTargetListItem
                onClick={props.handleSelectTarget}
                target={target}
                selected={props.selectedTargetId === target.target_id}
                key={target?.target_id}
              />
            ))}
          </List>
        </Box>
      ) : (
        <div>
          <Empty message={t('No target configured.')} />
        </div>
      )}
    </>
  );
};

export default PaginatedTargetTab;
