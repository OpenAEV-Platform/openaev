import { List } from '@mui/material';
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
}

const PaginatedTargetTab: React.FC<Props> = (props) => {
  const { t } = useFormatter();
  const pagination = useQueryableWithLocalStorage(props.target_type + '_' + props.inject_id + '_filters', buildSearchPagination({
    filterGroup: {
      mode: 'and',
      filters: [],
    },
  }));

  const [targets, setTargets] = useState<InjectTarget[]>();
  const [selectedTarget, setSelectedTarget] = useState<InjectTarget>();
  const [searchReloadContentCount, setSearchReloadContentCount] = useState(0);

  useEffect(() => {
    setSearchReloadContentCount(searchReloadContentCount + 1);
  }, [props.reloadContentCount]);

  const handleSetTargets = (content: InjectTarget[]) => {
    setTargets(content);
  };

  const handleSelectTarget = (target: InjectTarget) => {
    setSelectedTarget(target);
    props.handleSelectTarget(target);
  };

  useEffect(() => {
    if (targets && targets.length > 0 && !targets.find(t => t.target_id == selectedTarget?.target_id)) {
      handleSelectTarget(targets[0]);
    }
  }, [targets]);

  return (
    <>
      <PaginationComponentV2
        fetch={input => searchTargets(props.inject_id, props.target_type, input)}
        searchPaginationInput={pagination.searchPaginationInput}
        setContent={handleSetTargets}
        entityPrefix={props.entityPrefix}
        queryableHelpers={pagination.queryableHelpers}
        reloadContentCount={searchReloadContentCount}
        topPagination={true}
        contextId={props.inject_id}
      />
      {targets && targets.length > 0 ? (
        <List>
          {targets.map(target => (
            <NewTargetListItem
              onClick={handleSelectTarget}
              target={target}
              selected={selectedTarget?.target_id === target.target_id}
              key={target?.target_id}
            />
          ))}
        </List>
      ) : (
        <div>
          <Empty message={t('No target configured.')} />
        </div>
      )}
    </>
  );
};

export default PaginatedTargetTab;
