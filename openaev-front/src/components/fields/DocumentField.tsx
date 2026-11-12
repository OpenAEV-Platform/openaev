import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
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
}));

interface Props {
  label: string;
  fieldValue: string;
  fieldOnChange: (value: string) => void;
  error: FieldError | undefined;
  style: CSSProperties;
  extensions?: string[];
  // Id of a default document, shown by name as a faded placeholder while no document is picked.
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
}

const DocumentField: FunctionComponent<Props> = ({
  label,
  fieldValue,
  fieldOnChange,
  error,
  style,
  extensions = [],
  placeholder,
  disabled = false,
  required = false,
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
  const placeholderLabel = placeholder
    ? (documentsOptions.find(document => document.id === placeholder)?.label ?? placeholder)
    : undefined;

  return (
    <div style={{
      ...style,
      position: 'relative',
    }}
    >
      <Combobox<{
        id: string;
        label: string;
      }>
        required={required}
        disabled={disabled}
        options={documentsOptions}
        value={valueResolver() ?? null}
        onValueChange={(value) => {
          fieldOnChange((value as { id: string } | null)?.id ?? '');
        }}
        getOptionLabel={option => option.label}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        error={!!error}
        // The MUI field hid its clear control via a `classes` override.
        clearable={false}
        renderOption={option => (
          <>
            <div className={classes.icon}>
              <FileOutline />
            </div>
            <div className={classes.text}>{option.label}</div>
          </>
        )}
      >
        <ComboboxLabel required={required}>{label}</ComboboxLabel>
        <ComboboxField>
          <ComboboxInput placeholder={placeholderLabel} />
          <ComboboxControls>
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent />
        {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
      </Combobox>
    </div>
  );
};

export default DocumentField;
