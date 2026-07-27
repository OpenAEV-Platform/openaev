import { DeleteOutlined } from '@mui/icons-material';
import { Box, Button, Chip, CircularProgress, IconButton, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { FileChartOutline } from 'mdi-material-ui';
import { type CSSProperties, type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { deleteReportingGeneration, downloadReportingGenerationUrl } from '../../../actions/reporting/reporting-actions';
import DialogDelete from '../../../components/common/DialogDelete';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../components/common/queryable/sort/SortHelpers';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { type Header } from '../../../components/common/SortHeadersList';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import ItemStatus from '../../../components/ItemStatus';
import { type ReportingGeneration } from '../../../utils/api-types';
import { ReportingFormatFragment } from './ReportingFragments';

const LIST_POLL_INTERVAL_MS = 5000;

// i18n keys - translated through useFormatter's t()
const TRIGGER_LABELS: Record<ReportingGeneration['reporting_generation_trigger'], string> = {
  MANUAL: 'Manual',
  SCHEDULED: 'Scheduled',
};

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  reporting_generation_status: { width: '15%' },
  reporting_generation_format: { width: '10%' },
  reporting_generation_trigger: { width: '15%' },
  reporting_generation_created_at: { width: '25%' },
  reporting_generation_completed_at: { width: '25%' },
};

interface Props {
  generations: ReportingGeneration[];
  onReload: () => void;
  canManage: boolean;
  /** Empty-state CTA: generate the report in its default format. */
  onGenerate?: () => void;
  generating?: boolean;
}

/**
 * Generation history of a report as a sortable column list. Successful
 * generations download when the row is clicked; deletion stays as a row
 * action. The list polls itself while a generation is still in progress.
 */
const ReportingGenerationsTab: FunctionComponent<Props> = ({ generations, onReload, canManage, onGenerate, generating = false }) => {
  const { t, fldt } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const [generationToDelete, setGenerationToDelete] = useState<ReportingGeneration | null>(null);

  const hasActiveGeneration = generations.some(generation =>
    generation.reporting_generation_status === 'PENDING' || generation.reporting_generation_status === 'RUNNING');

  useEffect(() => {
    if (!hasActiveGeneration) return undefined;
    const timer = setInterval(onReload, LIST_POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [hasActiveGeneration, onReload]);

  const submitDelete = async () => {
    if (!generationToDelete) return;
    try {
      await deleteReportingGeneration(generationToDelete.reporting_generation_id);
      onReload();
    } finally {
      setGenerationToDelete(null);
    }
  };

  // Client-side sorting: the whole history is already loaded, so the sort
  // helpers just drive local state instead of a search endpoint.
  const [sortBy, setSortBy] = useState('reporting_generation_created_at');
  const [sortAsc, setSortAsc] = useState(false);
  const sortHelpers: SortHelpers = {
    handleSort: (field: string) => {
      if (field === sortBy) {
        setSortAsc(prev => !prev);
      } else {
        setSortBy(field);
        setSortAsc(true);
      }
    },
    handleDirectedSort: (field: string, asc: boolean) => {
      setSortBy(field);
      setSortAsc(asc);
    },
    getSortBy: () => sortBy,
    getSortAsc: () => sortAsc,
  };

  // All sortable fields are strings (enums or ISO dates) so a locale-aware
  // string comparison is enough.
  const sorted = useMemo(() => [...generations].sort((a, b) => {
    const first = String(a[sortBy as keyof ReportingGeneration] ?? '');
    const second = String(b[sortBy as keyof ReportingGeneration] ?? '');
    return sortAsc ? first.localeCompare(second) : second.localeCompare(first);
  }), [generations, sortBy, sortAsc]);

  const headers: Header[] = useMemo(() => [
    {
      field: 'reporting_generation_status',
      label: 'Status',
      isSortable: true,
      // Full plain status tag (same anatomy as inject statuses); the error
      // details surface through the tag's tooltip.
      value: (generation: ReportingGeneration) => (
        <ItemStatus
          label={t(generation.reporting_generation_status)}
          status={generation.reporting_generation_status}
          variant="inList"
          tooltipLabel={generation.reporting_generation_status === 'ERROR' && generation.reporting_generation_error
            ? generation.reporting_generation_error
            : undefined}
        />
      ),
    },
    {
      field: 'reporting_generation_format',
      label: 'Format',
      isSortable: true,
      value: (generation: ReportingGeneration) => (
        <ReportingFormatFragment format={generation.reporting_generation_format} />
      ),
    },
    {
      field: 'reporting_generation_trigger',
      label: 'Trigger',
      isSortable: true,
      value: (generation: ReportingGeneration) => (
        <Chip
          label={t(TRIGGER_LABELS[generation.reporting_generation_trigger])}
          variant="outlined"
          sx={{
            height: 20,
            fontSize: 12,
            textTransform: 'uppercase',
            borderRadius: 0.5,
            width: 100,
          }}
        />
      ),
    },
    {
      field: 'reporting_generation_created_at',
      label: 'Created at',
      isSortable: true,
      value: (generation: ReportingGeneration) => fldt(generation.reporting_generation_created_at),
    },
    {
      field: 'reporting_generation_completed_at',
      label: 'Completed at',
      isSortable: true,
      value: (generation: ReportingGeneration) => (generation.reporting_generation_completed_at
        ? fldt(generation.reporting_generation_completed_at)
        : '-'),
    },
  ], [t, fldt]);

  if (sorted.length === 0) {
    return (
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
      }}
      >
        <Empty
          icon={FileChartOutline}
          message={t('No generation yet.')}
          hint={t('Generate the report to produce its first downloadable output.')}
        />
        {canManage && onGenerate && (
          <Button
            variant="contained"
            color="primary"
            disabled={generating}
            startIcon={generating ? <CircularProgress size={14} color="inherit" /> : undefined}
            onClick={onGenerate}
            // Pull the CTA into the empty state's bottom padding.
            sx={{ marginTop: -3 }}
          >
            {generating ? t('Generating...') : t('Generate now')}
          </Button>
        )}
      </Box>
    );
  }

  const rowContent = (generation: ReportingGeneration) => (
    <>
      <ListItemIcon>
        <FileChartOutline color="primary" />
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
                {header.value?.(generation)}
              </div>
            ))}
          </div>
        )}
      />
    </>
  );

  return (
    <>
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          sx={{ pt: 0 }}
          secondaryAction={<>&nbsp;</>}
        >
          <ListItemIcon />
          <ListItemText
            primary={(
              <SortHeadersComponentV2
                headers={headers}
                inlineStylesHeaders={inlineStyles}
                sortHelpers={sortHelpers}
              />
            )}
          />
        </ListItem>
        {sorted.map((generation) => {
          const downloadable = generation.reporting_generation_status === 'SUCCESS' && generation.reporting_generation_document;
          return (
            <ListItem
              key={generation.reporting_generation_id}
              divider
              disablePadding={!!downloadable}
              classes={downloadable ? undefined : { root: classes.item }}
              secondaryAction={canManage
                ? (
                    <Tooltip title={t('Delete')}>
                      <IconButton size="small" color="primary" onClick={() => setGenerationToDelete(generation)}>
                        <DeleteOutlined fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )
                : undefined}
            >
              {downloadable
                ? (
                    <ListItemButton
                      component="a"
                      href={downloadReportingGenerationUrl(generation.reporting_generation_id)}
                      classes={{ root: classes.item }}
                    >
                      {rowContent(generation)}
                    </ListItemButton>
                  )
                : rowContent(generation)}
            </ListItem>
          );
        })}
      </List>
      <DialogDelete
        open={!!generationToDelete}
        handleClose={() => setGenerationToDelete(null)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this generation?')}
      />
    </>
  );
};

export default ReportingGenerationsTab;
