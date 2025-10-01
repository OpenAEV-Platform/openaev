import { Autocomplete as MuiAutocomplete, Box, TextField } from '@mui/material';
import { FileOutline } from 'mdi-material-ui';
import { type CSSProperties, type FunctionComponent } from 'react';
import { type FieldError } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { fetchDocuments } from '../../actions/Document';
import type { DocumentHelper } from '../../actions/helper';
import { useHelper } from '../../store';
import type { Document } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';

const useStyles = makeStyles()(() => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
  autoCompleteIndicator: { display: 'none' },
}));

interface Props {
  label: string;
  fieldValue: string;
  fieldOnChange: (value: string) => void;
  error: FieldError | undefined;
  style: CSSProperties;
  extensions?: string[];
}

const DocumentField: FunctionComponent<Props> = ({
  label,
  fieldValue,
  fieldOnChange,
  error,
  style,
  extensions = [],
}) => {
  const { classes } = useStyles();

  // Fetching data
  const { documents }: { documents: [Document] } = useHelper((helper: DocumentHelper) => ({ documents: helper.getDocuments() }));
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchDocuments());
  });

  // Form
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-expect-error
  const documentsOptions = documents.filter(n => (extensions.length > 0 ? extensions.includes(n.document_name.split('.').pop()) : true))
    .map(
      n => ({
        id: n.document_id,
        label: n.document_name,
      }),
    );
  const valueResolver = () => {
    return documentsOptions.filter(document => fieldValue === document.id).at(0);
  };

  return (
    <div style={{ position: 'relative' }}>
      <MuiAutocomplete
        value={valueResolver()}
        size="small"
        multiple={false}
        selectOnFocus={true}
        autoHighlight={true}
        clearOnBlur={false}
        clearOnEscape={false}
        options={documentsOptions}
        onChange={(_, value) => {
          fieldOnChange(value?.id ?? '');
        }}
        renderOption={(props, option) => (
          <Box component="li" {...props} key={option.id}>
            <div className={classes.icon}>
              <FileOutline />
            </div>
            <div className={classes.text}>{option.label}</div>
          </Box>
        )}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        renderInput={params => (
          <TextField
            {...params}
            label={label}
            variant="standard"
            fullWidth
            style={style}
            error={!!error}
          />
        )}
        classes={{ clearIndicator: classes.autoCompleteIndicator }}
      />
    </div>
  );
};

export default DocumentField;
