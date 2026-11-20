import { CSSProperties } from "react";
import { QueryableHelpers } from "../../../../components/common/queryable/QueryableHelpers";
import { FDS } from '../../../../components/fds-tokens.generated';
import FindingIcon from '../../../../components/FindingIcon';
import { useFormatter } from '../../../../components/i18n';
import ItemTargets from '../../../../components/ItemTargets';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { OpenCrqRowMark } from '../../../../components/xtm/XtmOpenCrq';
import { OpenCtiRowMark } from '../../../../components/xtm/XtmPivot';
import { type AggregatedFindingOutput, type TargetSimple } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';
import ContractOutputElementType from '../ContractOutputElementType';
import useBodyItemsStyles from "../../../../components/common/queryable/style/style";
import {Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText} from "@mui/material";
import {Tooltip, TooltipContent, TooltipTrigger } from "@filigran/design-system";
import markingsColumn from "../../../../components/common/list/markingsColumn";
import SortHeadersComponentV2 from "../../../../components/common/queryable/sort/SortHeadersComponentV2";
import {Binoculars} from "mdi-material-ui";
import {Link} from "react-router";
import Empty from "../../../../components/Empty";

interface Props {
  loading: boolean;
  findings: AggregatedFindingOutput[];
  queryableHelpers: QueryableHelpers;
  hiddenFields?: string[];
}

const inlineStyles: Record<string, CSSProperties> = ({
  finding_type: { width: '13%' },
  finding_value: { width: '27%' },
  finding_assets: { width: '20%' },
  finding_markings: { width: '12%' },
  finding_created_at: { width: '14%' },
  finding_updated_at: { width: '14%' },
});

const FindingListView = ({ loading, findings, queryableHelpers, hiddenFields = [] }: Props) => {
  const { t, nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();
  const markingEnabled = isFeatureEnabled('MARKING');

  const headers = [
    {
      field: 'finding_type',
      label: 'Type',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => (
        <span style={{
          fontWeight: 600,
          textTransform: 'uppercase',
          fontSize: 11,
          letterSpacing: '0.05em',
        }}
        >
          {t(ContractOutputElementType[finding.finding_type] ?? finding.finding_type)}
        </span>
      ),
    },
    {
      field: 'finding_value',
      label: 'Value',
      isSortable: true,
      // Findings are technical values (ports, sockets, hostnames, credentials...): render them
      // as inline code, mirroring the <pre> block of the finding overview page.
      value: (finding: AggregatedFindingOutput) => (
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          // 4 px between the value chip and its OpenCTI mark, as on every chip followed by a mark.
          gap: 0.5,
        }}
        >
          <Tooltip>
            <TooltipTrigger asChild>
              <Box
                component="code"
                sx={theme => ({
                  display: 'inline-block',
                  maxWidth: '95%',
                  padding: '2px 8px',
                  borderRadius: 1,
                  backgroundColor: theme.palette.background.accent,
                  border: `1px solid ${theme.palette.divider}`,
                  fontFamily: 'Consolas, monaco, monospace',
                  fontSize: FDS.scalars['--text-3'],
                  lineHeight: '18px',
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  verticalAlign: 'middle',
                })}
              >
                {finding.finding_value}
              </Box>
            </TooltipTrigger>
            {finding.finding_value && <TooltipContent>{finding.finding_value}</TooltipContent>}
          </Tooltip>
          {/* A CVE finding also known in OpenCTI or OpenCRQ carries the product mark linking to it. */}
          {finding.finding_type === 'cve' && (
            <>
              <OpenCtiRowMark kind="vulnerabilities" objectKey={finding.finding_value} />
              <OpenCrqRowMark kind="vulnerabilities" objectKey={finding.finding_value} />
            </>
          )}
        </Box>
      ),
    },
    {
      field: 'finding_assets',
      label: 'Assets',
      isSortable: false,
      value: (finding: AggregatedFindingOutput) => (
        <ItemTargets
          targets={(finding.finding_assets || []).map(asset => ({
            target_id: asset.asset_id,
            target_name: asset.asset_name,
            target_type: 'ASSETS',
            // Category + platform drive the chip glyph (taxonomy icon, or the OS brand icon
            // for host-like endpoints) - same rendering as the asset pages.
            target_category: asset.asset_category,
            target_subtype: asset.endpoint_platform,
          })) as TargetSimple[]}
        />
      ),
    },
    ...(markingEnabled
      ? [markingsColumn('finding_markings', (finding: AggregatedFindingOutput) => (finding.finding_assets ?? []).flatMap(asset => asset.asset_markings ?? []))]
      : []),
    {
      field: 'finding_created_at',
      label: 'First seen',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => <>{nsdt(finding.finding_created_at)}</>,
    },
    {
      field: 'finding_updated_at',
      label: 'Last seen',
      isSortable: true,
      value: (finding: AggregatedFindingOutput) => <>{nsdt(finding.finding_updated_at)}</>,
    },
  ];

  const visibleHeaders = headers.filter(h => !hiddenFields.includes(h.field));
  // Hiding columns (e.g. the compact drawer) leaves the fixed per-column widths summing to < 100%, which
  // squeezes and truncates the last columns (Assets). Rescale the visible columns proportionally so they
  // fill the row; a no-op in full mode where the widths already total 100%.
  const visibleWidthTotal = visibleHeaders.reduce(
    (sum, h) => sum + (parseFloat(String(inlineStyles[h.field]?.width ?? '0')) || 0),
    0,
  );
  const visibleStyles: Record<string, CSSProperties> = Object.fromEntries(
    visibleHeaders.map((h) => {
      const w = parseFloat(String(inlineStyles[h.field]?.width ?? '0')) || 0;
      return [h.field, {
        ...inlineStyles[h.field],
        ...(visibleWidthTotal > 0 && w > 0 ? { width: `${((w / visibleWidthTotal) * 100).toFixed(2)}%` } : {}),
      }];
    }),
  );

  return (
    <List>
      <ListItem
        sx={{
          textTransform: 'uppercase',
          paddingTop: 0,
        }}
      >
        <ListItemIcon />
        <ListItemText
          primary={(
            <SortHeadersComponentV2
              headers={visibleHeaders}
              inlineStylesHeaders={visibleStyles}
              sortHelpers={queryableHelpers.sortHelpers}
            />
          )}
        />
      </ListItem>
      {loading
        ? <PaginatedListLoader Icon={Binoculars} headers={visibleHeaders} headerStyles={visibleStyles} />
        : findings.map(finding => (
            <ListItem
              key={finding.finding_id}
              sx={{ height: 50 }}
              divider
              disablePadding
              data-testid="finding-row"
            >
              <ListItemButton
                sx={{ height: 50 }}
                component={Link}
                to={`/admin/findings/${finding.finding_id}`}
              >
                <ListItemIcon>
                  <FindingIcon findingType={finding.finding_type} />
                </ListItemIcon>
                <ListItemText
                  primary={(
                    <div style={bodyItemsStyles.bodyItems}>
                      {visibleHeaders.map(header => (
                        <div
                          key={header.field}
                          style={{
                            ...bodyItemsStyles.bodyItem,
                            ...visibleStyles[header.field],
                          }}
                        >
                          {header.value && header.value(finding)}
                        </div>
                      ))}
                    </div>
                  )}
                />
              </ListItemButton>
            </ListItem>
          ))}
      {!loading && findings.length === 0 && <Empty message={t('No finding found.')} />}
    </List>
  );
};

export default FindingListView;
