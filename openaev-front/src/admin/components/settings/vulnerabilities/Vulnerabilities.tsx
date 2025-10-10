import { HubOutlined, ReportProblemOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { searchVulnerabilities } from '../../../../actions/vulnerability-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import Drawer from '../../../../components/common/Drawer';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import CVSSBadge from '../../../../components/CvssBadge';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { type CveSimple, type SearchPaginationInput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import TaxonomiesMenu from '../TaxonomiesMenu';
import CreateVulnerability from './CreateVulnerability';
import VulnerabilityDetail from './VulnerabilityDetail';
import VulnerabilityPopover from './VulnerabilityPopover';

const useStyles = makeStyles()({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
});

const inlineStyles: Record<string, CSSProperties> = ({
  cve_external_id: { width: '20%' },
  cve_cvss_v31: { width: '20%' },
  cve_published: { width: '60%' },
});

const Vulnerabilities = () => {
  const { fldt, t } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const [loading, setLoading] = useState<boolean>(true);
  const [selectedCve, setSelectedCve] = useState<CveSimple | null>(null);
  // Filter
  const availableFilterNames = [
    'cve_external_id',
  ];
  const [vulnerabilities, setVulnerabilities] = useState<CveSimple[]>([]);
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('vulnerability', buildSearchPagination({
    sorts: initSorting('cve_created_at', 'DESC'),
    textSearch: search,
  }));

  const searchVulnerabilitiesToload = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchVulnerabilities(input).finally(() => {
      setLoading(false);
    });
  };

  const headers: Header[] = useMemo(() => [
    {
      field: 'cve_external_id',
      label: 'CVE ID',
      isSortable: true,
      value: (vulnerability: CveSimple) => vulnerability.cve_external_id,
    },
    {
      field: 'cve_cvss_v31',
      label: 'CVSS',
      isSortable: true,
      value: (vulnerability: CveSimple) => (
        <CVSSBadge score={vulnerability.cve_cvss_v31}></CVSSBadge>
      ),
    },
    {
      field: 'cve_published',
      label: 'NVD Published Date',
      isSortable: true,
      value: (vulnerability: CveSimple) => fldt(vulnerability.cve_published),
    },
  ], []);

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Settings') }, { label: t('Taxonomies') }, {
            label: t('Vulnerabilities'),
            current: true,
          }]}
        />
        <PaginationComponentV2
          fetch={searchVulnerabilitiesToload}
          searchPaginationInput={searchPaginationInput}
          setContent={setVulnerabilities}
          availableFilterNames={availableFilterNames}
          queryableHelpers={queryableHelpers}
          entityPrefix="vulnerability"
        />
        <List>
          <ListItem
            classes={{ root: classes.itemHead }}
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

          {loading ? <PaginatedListLoader Icon={HubOutlined} headers={headers} headerStyles={inlineStyles} /> : vulnerabilities.map(vulnerability => (
            <ListItem
              key={vulnerability.cve_id}
              divider
              disablePadding
              secondaryAction={(
                <VulnerabilityPopover
                  vulnerability={vulnerability}
                  onUpdate={(result: CveSimple) => setVulnerabilities(vulnerabilities.map(a => (a.cve_id !== result.cve_id ? a : result)))}
                  onDelete={(result: string) => setVulnerabilities(vulnerabilities.filter(a => (a.cve_id !== result)))}
                />
              )}
            >
              <ListItemButton
                classes={{ root: classes.item }}
                onClick={() => setSelectedCve(vulnerability)}
              >
                <ListItemIcon>
                  <ReportProblemOutlined />
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
                          {header.value && header.value(vulnerability)}
                        </div>
                      ))}
                    </div>
                  )}
                />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
          <CreateVulnerability
            onCreate={(result: CveSimple) => setVulnerabilities([result, ...vulnerabilities])}
          />
        </Can>
        <Drawer
          open={!!selectedCve}
          handleClose={() => setSelectedCve(null)}
          title={selectedCve?.cve_external_id ?? ''}
          additionalTitle={selectedCve?.cve_cvss_v31 ? 'CVSS' : undefined}
          additionalChipLabel={selectedCve?.cve_cvss_v31.toFixed(1)}
        >
          {selectedCve && (
            <VulnerabilityDetail
              selectedCve={selectedCve}
            />
          )}
        </Drawer>
      </div>
      <TaxonomiesMenu />
    </div>
  );
};

export default Vulnerabilities;
