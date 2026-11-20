import { searchDistinctFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import {Box} from "@mui/material";
import MarketplaceBrowser from "../../../components/common/marketplace/page/MarketplaceBrowser";
import FindingHero from "./components/FindingHero";
import {useQueryableWithLocalStorage} from "../../../components/common/queryable/useQueryableWithLocalStorage";
import {buildSearchPagination} from "../../../components/common/queryable/QueryableUtils";
import {initSorting} from "../../../components/common/queryable/Page";
import PaginationComponentV2 from "../../../components/common/queryable/pagination/PaginationComponentV2";
import {useState} from "react";
import {AggregatedFindingOutput, SearchPaginationInput} from "../../../utils/api-types";
import {ViewMode} from "../../../components/common/marketplace/toolbar/useMarketplaceView";
import FindingListView from "./components/FindingListView";
import FindingSidebar from "./components/FindingSidebar";
import FindingCardView from "./components/FindingCardView";
import {useTheme} from "@mui/material/styles";

const Findings = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [loading, setLoading] = useState<boolean>(true);
  const [mode, setMode] = useState<ViewMode>();
  const [findings, setFindings] = useState<AggregatedFindingOutput[]>([]);
  const [total, setTotal] = useState<number>(0);

  const availableFilterNames = [
      'finding_type',
      'finding_created_at',
      'finding_updated_at',
      'finding_asset_groups',
      'finding_assets',
  ];

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
      `findings-v2`,
      buildSearchPagination({
          sorts: initSorting('finding_updated_at', 'DESC'),
      }),
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
        }}>
            <MarketplaceBrowser
                hero={
                    <FindingHero
                        activeFindings={total}
                        onViewModeChange={setMode}
                    />
                }
                sidebar={
                    <FindingSidebar
                        searchPaginationInput={searchPaginationInput}
                        filterHelpers={queryableHelpers.filterHelpers}
                    />
                }
                toolbar={<></>}
                mainBoxStyle={{ gap: 2 }}
                childrenBoxStyle={{ gap: 0 }}
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
                    {mode === 'list' ?
                        (<FindingListView
                            loading={loading}
                            findings={findings}
                            queryableHelpers={queryableHelpers}
                        />) :
                        (<FindingCardView
                            loading={loading}
                            findings={findings}
                        />)}
                </>
            </MarketplaceBrowser>
        </Box>
    </>
  );
};

export default Findings;
