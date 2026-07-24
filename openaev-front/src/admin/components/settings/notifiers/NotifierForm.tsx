import { AddOutlined, DeleteOutlined } from '@mui/icons-material';
import { Button, IconButton, MenuItem, TextField, Typography } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type NotifierInput, type NotifierOutput } from '../../../../utils/api-types';

interface HeaderPair {
  key: string;
  value: string;
}

interface Props {
  onSubmit: (input: NotifierInput) => void;
  editing?: boolean;
  initialValues?: NotifierOutput;
}

// The UI notifier is built-in only (aligned with OpenCTI): users can only
// create email and webhook notifiers.
const NOTIFIER_TYPES = ['EMAIL', 'WEBHOOK'] as const;

/** Creation / edition form for notifiers, with type-specific configuration fields. */
const NotifierForm: FunctionComponent<Props> = ({
  onSubmit,
  editing,
  initialValues,
}) => {
  const { t } = useFormatter();

  const configuration = initialValues?.notifier_configuration ?? {};
  const initialHeaders: HeaderPair[] = Object.entries((configuration.headers as Record<string, string> | undefined) ?? {})
    .map(([key, value]) => ({
      key,
      value: String(value),
    }));

  const [name, setName] = useState(initialValues?.notifier_name ?? '');
  const [description, setDescription] = useState(initialValues?.notifier_description ?? '');
  const [type, setType] = useState<'EMAIL' | 'WEBHOOK'>(
    initialValues?.notifier_type === 'WEBHOOK' ? 'WEBHOOK' : 'EMAIL',
  );
  const [subject, setSubject] = useState(String(configuration.subject ?? ''));
  const [template, setTemplate] = useState(String(configuration.template ?? ''));
  const [url, setUrl] = useState(String(configuration.url ?? ''));
  const [verb, setVerb] = useState(String(configuration.verb ?? 'POST'));
  const [headers, setHeaders] = useState<HeaderPair[]>(initialHeaders);
  const [submitted, setSubmitted] = useState(false);

  const nameError = submitted && !name.trim() ? t('Should not be empty') : undefined;
  const urlError = submitted && type === 'WEBHOOK' && !/^https?:\/\//.test(url.trim())
    ? t('A valid http(s) URL is required')
    : undefined;

  const handleSubmit = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setSubmitted(true);
    if (!name.trim() || (type === 'WEBHOOK' && !/^https?:\/\//.test(url.trim()))) {
      return;
    }
    const config: Record<string, unknown> = {};
    if (type === 'EMAIL') {
      if (subject.trim()) config.subject = subject.trim();
      if (template.trim()) config.template = template.trim();
    }
    if (type === 'WEBHOOK') {
      config.url = url.trim();
      config.verb = verb;
      if (template.trim()) config.template = template.trim();
      const headersMap = Object.fromEntries(headers
        .filter(header => header.key.trim())
        .map(header => [header.key.trim(), header.value]));
      if (Object.keys(headersMap).length > 0) config.headers = headersMap;
    }
    onSubmit({
      notifier_name: name.trim(),
      notifier_description: description.trim() || undefined,
      notifier_type: type,
      notifier_configuration: config,
    });
  };

  return (
    <form id="notifierForm" onSubmit={handleSubmit}>
      <TextField
        variant="standard"
        fullWidth
        label={t('Name')}
        value={name}
        onChange={e => setName(e.target.value)}
        error={!!nameError}
        helperText={nameError}
      />
      <TextField
        variant="standard"
        fullWidth
        label={t('Description')}
        value={description}
        onChange={e => setDescription(e.target.value)}
        style={{ marginTop: 20 }}
      />
      <TextField
        variant="standard"
        fullWidth
        select
        label={t('Type')}
        value={type}
        onChange={e => setType(e.target.value as typeof type)}
        style={{ marginTop: 20 }}
        disabled={editing}
      >
        {NOTIFIER_TYPES.map((option) => {
          const labels: Record<string, string> = {
            EMAIL: 'Email',
            WEBHOOK: 'Webhook',
          };
          return (
            <MenuItem key={option} value={option}>
              {t(labels[option])}
            </MenuItem>
          );
        })}
      </TextField>
      {type === 'EMAIL' && (
        <>
          <TextField
            variant="standard"
            fullWidth
            label={t('Subject template')}
            value={subject}
            onChange={e => setSubject(e.target.value)}
            style={{ marginTop: 20 }}
            // eslint-disable-next-line no-template-curly-in-string
            placeholder="[OpenAEV] ${notification_name}"
          />
          <TextField
            variant="standard"
            fullWidth
            multiline
            minRows={6}
            label={t('Body template (FreeMarker, empty = default template)')}
            value={template}
            onChange={e => setTemplate(e.target.value)}
            style={{ marginTop: 20 }}
          />
        </>
      )}
      {type === 'WEBHOOK' && (
        <>
          <TextField
            variant="standard"
            fullWidth
            label={t('URL')}
            value={url}
            onChange={e => setUrl(e.target.value)}
            style={{ marginTop: 20 }}
            error={!!urlError}
            helperText={urlError}
          />
          <TextField
            variant="standard"
            fullWidth
            select
            label={t('Verb')}
            value={verb}
            onChange={e => setVerb(e.target.value)}
            style={{ marginTop: 20 }}
          >
            {['POST', 'PUT', 'GET', 'DELETE'].map(option => (
              <MenuItem key={option} value={option}>{option}</MenuItem>
            ))}
          </TextField>
          <Typography variant="h5" style={{ marginTop: 20 }}>{t('Headers')}</Typography>
          {headers.map((header, index) => (
            // eslint-disable-next-line react/no-array-index-key
            <div
              key={index}
              style={{
                display: 'flex',
                gap: 10,
                alignItems: 'end',
              }}
            >
              <TextField
                variant="standard"
                label={t('Key')}
                value={header.key}
                onChange={e => setHeaders(headers.map((existing, i) => (i === index
                  ? {
                      ...existing,
                      key: e.target.value,
                    }
                  : existing)))}
                style={{ flex: 1 }}
              />
              <TextField
                variant="standard"
                label={t('Value')}
                value={header.value}
                onChange={e => setHeaders(headers.map((existing, i) => (i === index
                  ? {
                      ...existing,
                      value: e.target.value,
                    }
                  : existing)))}
                style={{ flex: 2 }}
              />
              <IconButton
                size="small"
                color="error"
                onClick={() => setHeaders(headers.filter((_, i) => i !== index))}
              >
                <DeleteOutlined fontSize="small" />
              </IconButton>
            </div>
          ))}
          <Button
            size="small"
            startIcon={<AddOutlined />}
            onClick={() => setHeaders([...headers, {
              key: '',
              value: '',
            }])}
            style={{ marginTop: 10 }}
          >
            {t('Add header')}
          </Button>
          <TextField
            variant="standard"
            fullWidth
            multiline
            minRows={6}
            label={t('Body template (FreeMarker, empty = default JSON payload)')}
            value={template}
            onChange={e => setTemplate(e.target.value)}
            style={{ marginTop: 20 }}
          />
        </>
      )}
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

export default NotifierForm;
