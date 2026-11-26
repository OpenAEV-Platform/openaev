import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { searchDistinctFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import MarketplaceBrowser from '../../../components/common/marketplace/page/MarketplaceBrowser';
import useMarketplaceView from '../../../components/common/marketplace/toolbar/useMarketplaceView';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { type AggregatedFindingOutput, type SearchPaginationInput } from '../../../utils/api-types';
import FindingCardView from './components/FindingCardView';
import FindingHero from './components/FindingHero';
import FindingListView from './components/FindingListView';
import FindingSidebar from './components/FindingSidebar';

const Findings = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [loading, setLoading] = useState<boolean>(true);
  const { viewMode, handleViewModeChange } = useMarketplaceView('findings:view-mode');
  const [findings, setFindings] = useState<AggregatedFindingOutput[]>([]);
  const [total, setTotal] = useState<number>(0);

  const availableFilterNames = [
    'finding_type',
    'finding_created_at',
    'finding_updated_at',
    'finding_asset_groups',
    'finding_assets',
    'finding_sources',
  ];

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    `findings-v2`,
    buildSearchPagination({ sorts: initSorting('finding_updated_at', 'DESC') }),
  );
  const searchFindingsToload = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchDistinctFindings(input)
      .then((res) => {
        setTotal(res.data.totalElements);
        return res;
      })
      .finally(() => {
        setLoading(false);
      });
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Findings'),
          current: true,
        }]}
      />
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
      }}
      >
        <MarketplaceBrowser
          hero={(
            <FindingHero
              activeFindings={total}
              viewMode={viewMode}
              onViewModeChange={handleViewModeChange}
            />
          )}
          sidebar={(
            <FindingSidebar
              searchPaginationInput={searchPaginationInput}
              filterHelpers={queryableHelpers.filterHelpers}
            />
          )}
          toolbar={<></>}
        >
          <>
            <PaginationComponentV2
              fetch={searchFindingsToload}
              searchPaginationInput={searchPaginationInput}
              setContent={setFindings}
              entityPrefix="finding"
              availableFilterNames={availableFilterNames}
              queryableHelpers={queryableHelpers}
              searchEnable={true}
              disableFilters={false}
              disablePagination={false}
              mainBoxStyle={{ marginBottom: theme.spacing(1) }}
            />
            {viewMode === 'list'
              ? (
                  <FindingListView
                    loading={loading}
                    findings={findings}
                    queryableHelpers={queryableHelpers}
                  />
                )
              : (
                  <FindingCardView
                    loading={loading}
                    findings={findings}
                  />
                )}
          </>
        </MarketplaceBrowser>
      </Box>
    </>
  );
};

export default Findings;
