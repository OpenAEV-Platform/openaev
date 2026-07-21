import { Autocomplete, Button, Checkbox, Chip, FormControlLabel, FormGroup, MenuItem, TextField } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent, useEffect, useState } from 'react';

import { searchNotificationTriggers } from '../../../../actions/notifications/notification-trigger-actions';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../../components/i18n';
import { type FilterGroup, type NotificationTriggerInput, type NotificationTriggerOutput } from '../../../../utils/api-types';
import NotifierField from './NotifierField';
import TriggerFilterField from './TriggerFilterField';
import { buildTriggerTime, DIGEST_PERIODS, eventTypeLabel, parseTriggerTime, resourceTypeEntityPrefix, SCENARIO_ONLY_EVENT_TYPES, TRIGGER_EVENT_TYPES, TRIGGER_RESOURCE_TYPES, WEEK_DAYS } from './triggerUtils';

interface Props {
  triggerType: 'LIVE' | 'DIGEST';
  onSubmit: (input: NotificationTriggerInput) => void;
  editing?: boolean;
  initialValues?: NotificationTriggerOutput;
}

/** Creation / edition form for live and digest notification triggers. */
const TriggerForm: FunctionComponent<Props> = ({
  triggerType,
  onSubmit,
  editing,
  initialValues,
}) => {
  const { t } = useFormatter();

  const initialTime = parseTriggerTime(initialValues?.notification_trigger_time);

  const [name, setName] = useState(initialValues?.notification_trigger_name ?? '');
  const [resourceType, setResourceType] = useState<NotificationTriggerInput['notification_trigger_resource_type']>(
    initialValues?.notification_trigger_resource_type ?? 'SCENARIO',
  );
  const [eventTypes, setEventTypes] = useState<string[]>(initialValues?.notification_trigger_event_types ?? ['CREATE']);
  const [filters, setFilters] = useState<FilterGroup | undefined>(initialValues?.notification_trigger_filters);
  const [notifierIds, setNotifierIds] = useState<string[]>(initialValues?.notification_trigger_notifiers ?? []);
  const [period, setPeriod] = useState(initialValues?.notification_trigger_period ?? 'DAY');
  const [day, setDay] = useState(initialTime.day);
  const [time, setTime] = useState(initialTime.time);
  const [childTriggerIds, setChildTriggerIds] = useState<string[]>(initialValues?.notification_trigger_children ?? []);
  const [liveTriggers, setLiveTriggers] = useState<NotificationTriggerOutput[]>([]);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    if (triggerType === 'DIGEST') {
      searchNotificationTriggers(buildSearchPagination({ size: 100 }))
        .then((result: { data: { content?: NotificationTriggerOutput[] } }) => {
          const content = result.data.content ?? [];
          setLiveTriggers(content.filter(trigger => trigger.notification_trigger_type === 'LIVE'));
        });
    }
  }, [triggerType]);

  const toggleEventType = (eventType: string) => {
    setEventTypes(eventTypes.includes(eventType)
      ? eventTypes.filter(existing => existing !== eventType)
      : [...eventTypes, eventType]);
  };

  const nameError = submitted && !name.trim() ? t('Should not be empty') : undefined;
  const eventTypesError = submitted && triggerType === 'LIVE' && eventTypes.length === 0
    ? t('Select at least one event type')
    : undefined;
  const notifiersError = submitted && notifierIds.length === 0 ? t('Select at least one notifier') : undefined;
  const childrenError = submitted && triggerType === 'DIGEST' && childTriggerIds.length === 0
    ? t('Select at least one trigger')
    : undefined;

  const handleSubmit = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setSubmitted(true);
    if (!name.trim() || notifierIds.length === 0) {
      return;
    }
    if (triggerType === 'LIVE' && eventTypes.length === 0) {
      return;
    }
    if (triggerType === 'DIGEST' && childTriggerIds.length === 0) {
      return;
    }
    const input: NotificationTriggerInput = {
      notification_trigger_name: name.trim(),
      notification_trigger_type: triggerType,
      notification_trigger_enabled: initialValues?.notification_trigger_enabled ?? true,
      notification_trigger_notifiers: notifierIds,
      ...(triggerType === 'LIVE'
        ? {
            notification_trigger_resource_type: resourceType,
            notification_trigger_event_types: eventTypes as NotificationTriggerInput['notification_trigger_event_types'],
            notification_trigger_filters: filters,
            notification_trigger_instance_id: initialValues?.notification_trigger_instance_id,
          }
        : {
            notification_trigger_period: period,
            notification_trigger_time: buildTriggerTime(period, day, time),
            notification_trigger_children: childTriggerIds,
          }),
    };
    onSubmit(input);
  };

  const entityPrefix = resourceTypeEntityPrefix(resourceType);
  const selectedChildren = liveTriggers.filter(trigger => childTriggerIds.includes(trigger.notification_trigger_id));

  return (
    <form id="triggerForm" onSubmit={handleSubmit}>
      <TextField
        variant="standard"
        fullWidth
        label={t('Name')}
        value={name}
        onChange={e => setName(e.target.value)}
        error={!!nameError}
        helperText={nameError}
      />
      {triggerType === 'LIVE' && (
        <>
          <TextField
            variant="standard"
            fullWidth
            select
            label={t('Resource type')}
            value={resourceType}
            onChange={(e) => {
              setResourceType(e.target.value as NotificationTriggerInput['notification_trigger_resource_type']);
              setFilters(undefined);
              if (e.target.value !== 'SCENARIO') {
                // Score degradation is only available for scenarios
                setEventTypes(existing => existing.filter(eventType => eventType !== 'SCORE_DEGRADATION'));
              }
            }}
            style={{ marginTop: 20 }}
            disabled={!!initialValues?.notification_trigger_instance_id}
          >
            {TRIGGER_RESOURCE_TYPES.map(option => (
              <MenuItem key={option.value} value={option.value}>
                {t(option.label)}
              </MenuItem>
            ))}
          </TextField>
          <FormGroup row style={{ marginTop: 20 }}>
            {[
              ...TRIGGER_EVENT_TYPES,
              // Score degradation is a scenario-only semantic event
              ...(resourceType === 'SCENARIO' ? SCENARIO_ONLY_EVENT_TYPES : []),
            ].map(eventType => (
              <FormControlLabel
                key={eventType}
                control={(
                  <Checkbox
                    checked={eventTypes.includes(eventType)}
                    onChange={() => toggleEventType(eventType)}
                  />
                )}
                label={t(eventTypeLabel(eventType))}
              />
            ))}
          </FormGroup>
          {eventTypesError && (
            <div style={{
              color: '#f44336',
              fontSize: 12,
            }}
            >
              {eventTypesError}
            </div>
          )}
          {!initialValues?.notification_trigger_instance_id && entityPrefix && (
            <TriggerFilterField
              key={entityPrefix}
              entityPrefix={entityPrefix}
              value={filters}
              onChange={setFilters}
            />
          )}
        </>
      )}
      {triggerType === 'DIGEST' && (
        <>
          <Autocomplete
            multiple
            options={liveTriggers}
            value={selectedChildren}
            onChange={(_, newValue) => setChildTriggerIds(newValue.map(trigger => trigger.notification_trigger_id))}
            getOptionLabel={trigger => trigger.notification_trigger_name ?? ''}
            isOptionEqualToValue={(option, val) => option.notification_trigger_id === val.notification_trigger_id}
            style={{ marginTop: 20 }}
            renderTags={(tagValue, getTagProps) =>
              tagValue.map((option, index) => (
                <Chip
                  {...getTagProps({ index })}
                  key={option.notification_trigger_id}
                  label={option.notification_trigger_name}
                  size="small"
                />
              ))}
            renderInput={params => (
              <TextField
                {...params}
                variant="standard"
                label={t('Composed triggers')}
                error={!!childrenError}
                helperText={childrenError}
              />
            )}
          />
          <TextField
            variant="standard"
            fullWidth
            select
            label={t('Period')}
            value={period}
            onChange={e => setPeriod(e.target.value as typeof period)}
            style={{ marginTop: 20 }}
          >
            {DIGEST_PERIODS.map(option => (
              <MenuItem key={option} value={option}>
                {t(option.charAt(0) + option.slice(1).toLowerCase())}
              </MenuItem>
            ))}
          </TextField>
          {period === 'WEEK' && (
            <TextField
              variant="standard"
              fullWidth
              select
              label={t('Day of week')}
              value={day}
              onChange={e => setDay(e.target.value)}
              style={{ marginTop: 20 }}
            >
              {WEEK_DAYS.map(option => (
                <MenuItem key={option.value} value={option.value}>
                  {t(option.label)}
                </MenuItem>
              ))}
            </TextField>
          )}
          {period === 'MONTH' && (
            <TextField
              variant="standard"
              fullWidth
              select
              label={t('Day of month')}
              value={day}
              onChange={e => setDay(e.target.value)}
              style={{ marginTop: 20 }}
            >
              {Array.from({ length: 31 }, (_, index) => `${index + 1}`).map(option => (
                <MenuItem key={option} value={option}>
                  {option}
                </MenuItem>
              ))}
            </TextField>
          )}
          {period !== 'HOUR' && (
            <TextField
              variant="standard"
              fullWidth
              type="time"
              label={t('Time (UTC)')}
              value={time}
              onChange={e => setTime(e.target.value)}
              style={{ marginTop: 20 }}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          )}
        </>
      )}
      <NotifierField
        value={notifierIds}
        onChange={setNotifierIds}
        error={notifiersError}
        style={{ marginTop: 20 }}
      />
      <div style={{
        float: 'right',
        marginTop: 20,
      }}
      >
        <Button
          variant="contained"
          color="primary"
          type="submit"
        >
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default TriggerForm;
