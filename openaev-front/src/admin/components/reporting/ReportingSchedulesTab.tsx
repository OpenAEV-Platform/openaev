import { AddOutlined, DeleteOutlined, EditOutlined, ScheduleOutlined } from '@mui/icons-material';
import { Box, Button, Chip, IconButton, List, ListItem, ListItemIcon, ListItemText, Switch, Tooltip, Typography } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import {
  createReportingSchedule,
  deleteReportingSchedule,
  updateReportingSchedule,
} from '../../../actions/reporting/reporting-actions';
import ButtonCreate from '../../../components/common/ButtonCreate';
import DialogDelete from '../../../components/common/DialogDelete';
import Drawer from '../../../components/common/Drawer';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../components/common/queryable/sort/SortHelpers';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { type Header } from '../../../components/common/SortHeadersList';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import SearchFilter from '../../../components/SearchFilter';
import { type Reporting, type ReportingSchedule, type ReportingScheduleInput } from '../../../utils/api-types';
import { type ReportingScheduleFieldsValues } from './form/ReportingScheduleFields';
import ReportingScheduleForm from './form/ReportingScheduleForm';
import { describeScheduleTime, scheduleInputFromValues, scheduleValuesFromSchedule } from './form/reportingScheduleUtils';
import { SCHEDULE_PERIOD_LABELS } from './ReportingFormUtils';
import { ReportingFormatFragment } from './ReportingFragments';

/** Full API input mirroring an existing schedule (partial PUTs are not supported). */
const inputFromSchedule = (schedule: ReportingSchedule, enabled: boolean): ReportingScheduleInput => ({
  reporting_schedule_name: schedule.reporting_schedule_name,
  reporting_schedule_period: schedule.reporting_schedule_period,
  reporting_schedule_time: schedule.reporting_schedule_time,
  reporting_schedule_format: schedule.reporting_schedule_format,
  reporting_schedule_enabled: enabled,
  reporting_schedule_recipient_users: schedule.reporting_schedule_recipient_users,
  reporting_schedule_recipient_emails: schedule.reporting_schedule_recipient_emails,
});

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  reporting_schedule_name: { width: '22%' },
  reporting_schedule_period: { width: '30%' },
  reporting_schedule_format: { width: '10%' },
  reporting_schedule_recipients: { width: '23%' },
};

interface Props {
  reporting: Reporting;
  onChanged: () => void;
  canManage: boolean;
}

/**
 * Recurring generations of a report as a searchable, sortable column list:
 * enable/disable toggle, add / edit (drawer reusing the wizard's schedule
 * fields) and deletion.
 */
const ReportingSchedulesTab: FunctionComponent<Props> = ({ reporting, onChanged, canManage }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const [drawer, setDrawer] = useState<'create' | ReportingSchedule | null>(null);
  const [scheduleToDelete, setScheduleToDelete] = useState<ReportingSchedule | null>(null);
  const [keyword, setKeyword] = useState('');

  const schedules = reporting.reporting_schedules ?? [];

  const onSubmit = async (values: ReportingScheduleFieldsValues) => {
    try {
      const input = scheduleInputFromValues(values);
      if (drawer === 'create') {
        await createReportingSchedule(reporting.reporting_id, input);
      } else if (drawer) {
        await updateReportingSchedule(reporting.reporting_id, drawer.reporting_schedule_id, input);
      }
      onChanged();
    } finally {
      setDrawer(null);
    }
  };

  const toggleEnabled = async (schedule: ReportingSchedule, enabled: boolean) => {
    await updateReportingSchedule(
      reporting.reporting_id,
      schedule.reporting_schedule_id,
      inputFromSchedule(schedule, enabled),
    );
    onChanged();
  };

  const submitDelete = async () => {
    if (!scheduleToDelete) return;
    try {
      await deleteReportingSchedule(reporting.reporting_id, scheduleToDelete.reporting_schedule_id);
      onChanged();
    } finally {
      setScheduleToDelete(null);
    }
  };

  const recipientsSummary = (schedule: ReportingSchedule) => {
    const userCount = schedule.reporting_schedule_recipient_users?.length ?? 0;
    const emailCount = schedule.reporting_schedule_recipient_emails?.length ?? 0;
    if (userCount === 0 && emailCount === 0) return t('No recipients');
    return [
      userCount > 0 ? `${userCount} ${t('users')}` : null,
      emailCount > 0 ? `${emailCount} ${t('emails')}` : null,
    ].filter(Boolean).join(' - ');
  };

  const periodSummary = (schedule: ReportingSchedule) => {
    const period = t(SCHEDULE_PERIOD_LABELS[schedule.reporting_schedule_period]);
    const timeSummary = describeScheduleTime(schedule, t);
    return timeSummary ? `${period} ${t('at')} ${timeSummary}` : period;
  };

  // Client-side sorting: schedules ship with the report, so the sort helpers
  // drive local state. Computed columns sort on their displayed value.
  const sortValues: Record<string, (schedule: ReportingSchedule) => string | number> = {
    reporting_schedule_name: schedule => schedule.reporting_schedule_name || t('Schedule'),
    reporting_schedule_period: schedule => periodSummary(schedule),
    reporting_schedule_format: schedule => schedule.reporting_schedule_format ?? '',
    reporting_schedule_recipients: schedule => (schedule.reporting_schedule_recipient_users?.length ?? 0)
      + (schedule.reporting_schedule_recipient_emails?.length ?? 0),
  };

  const [sortBy, setSortBy] = useState('reporting_schedule_name');
  const [sortAsc, setSortAsc] = useState(true);
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

  const filtered = schedules.filter((schedule) => {
    const search = keyword.trim().toLowerCase();
    if (!search) return true;
    return [
      schedule.reporting_schedule_name || t('Schedule'),
      periodSummary(schedule),
      schedule.reporting_schedule_format,
      recipientsSummary(schedule),
    ].some(value => String(value).toLowerCase().includes(search));
  });

  const sorted = [...filtered].sort((a, b) => {
    const valueOf = sortValues[sortBy] ?? sortValues.reporting_schedule_name;
    const first = valueOf(a);
    const second = valueOf(b);
    const compared = typeof first === 'number' && typeof second === 'number'
      ? first - second
      : String(first).localeCompare(String(second));
    return sortAsc ? compared : -compared;
  });

  const headers: Header[] = useMemo(() => [
    {
      field: 'reporting_schedule_name',
      label: 'Name',
      isSortable: true,
      value: (schedule: ReportingSchedule) => (
        <Typography variant="body2" sx={{ fontWeight: 600 }} component="span">
          {schedule.reporting_schedule_name || t('Schedule')}
        </Typography>
      ),
    },
    {
      field: 'reporting_schedule_period',
      label: 'Recurrence',
      isSortable: true,
      value: (schedule: ReportingSchedule) => (
        <Chip
          label={periodSummary(schedule)}
          variant="outlined"
          sx={{
            height: 20,
            fontSize: 12,
            borderRadius: 0.5,
          }}
        />
      ),
    },
    {
      field: 'reporting_schedule_format',
      label: 'Format',
      isSortable: true,
      value: (schedule: ReportingSchedule) => (
        <ReportingFormatFragment format={schedule.reporting_schedule_format} />
      ),
    },
    {
      field: 'reporting_schedule_recipients',
      label: 'Recipients',
      isSortable: true,
      value: (schedule: ReportingSchedule) => recipientsSummary(schedule),
    },
  ], [t]);

  return (
    <>
      {/* No schedule at all: full design-system empty state with a centered
          CTA (the search / add top bar would be dead weight here). */}
      {schedules.length === 0
        ? (
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
            >
              <Empty
                icon={ScheduleOutlined}
                message={t('No schedule yet.')}
                hint={t('Schedules generate this report on a recurring basis and email it to recipients.')}
              />
              {canManage && (
                <Button
                  variant="contained"
                  color="primary"
                  startIcon={<AddOutlined />}
                  onClick={() => setDrawer('create')}
                  // Pull the CTA into the empty state's bottom padding.
                  sx={{ marginTop: -3 }}
                >
                  {t('Add schedule')}
                </Button>
              )}
            </Box>
          )
        : (
            <>
              <Box sx={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: 1,
                marginTop: 2,
              }}
              >
                <SearchFilter
                  variant="small"
                  keyword={keyword}
                  onChange={(value?: string) => setKeyword(value ?? '')}
                />
                {canManage && <ButtonCreate label={t('Add schedule')} onClick={() => setDrawer('create')} />}
              </Box>
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
                {sorted.length === 0 && (
                  <Empty message={t('No results found')} />
                )}
                {sorted.map(schedule => (
                  <ListItem
                    key={schedule.reporting_schedule_id}
                    divider
                    classes={{ root: classes.item }}
                    secondaryAction={(
                      <Box display="flex" alignItems="center" gap={0.5}>
                        <Tooltip title={schedule.reporting_schedule_enabled ? t('Disable') : t('Enable')}>
                          <Switch
                            size="small"
                            checked={schedule.reporting_schedule_enabled}
                            disabled={!canManage}
                            onChange={(_, checked) => toggleEnabled(schedule, checked)}
                          />
                        </Tooltip>
                        {canManage && (
                          <>
                            <Tooltip title={t('Update')}>
                              <IconButton size="small" color="primary" onClick={() => setDrawer(schedule)}>
                                <EditOutlined fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title={t('Delete')}>
                              <IconButton size="small" color="primary" onClick={() => setScheduleToDelete(schedule)}>
                                <DeleteOutlined fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </>
                        )}
                      </Box>
                    )}
                  >
                    <ListItemIcon>
                      <ScheduleOutlined color="primary" />
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
                              {header.value?.(schedule)}
                            </div>
                          ))}
                        </div>
                      )}
                    />
                  </ListItem>
                ))}
              </List>
            </>
          )}
      <Drawer
        open={drawer !== null}
        handleClose={() => setDrawer(null)}
        title={drawer === 'create' ? t('Add a schedule') : t('Update the schedule')}
      >
        <ReportingScheduleForm
          onSubmit={onSubmit}
          handleClose={() => setDrawer(null)}
          initialValues={scheduleValuesFromSchedule(drawer === 'create' || drawer === null ? undefined : drawer)}
          editing={drawer !== 'create' && drawer !== null}
        />
      </Drawer>
      <DialogDelete
        open={!!scheduleToDelete}
        handleClose={() => setScheduleToDelete(null)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this schedule?')}
      />
    </>
  );
};

export default ReportingSchedulesTab;
