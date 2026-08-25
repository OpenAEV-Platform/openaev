import { zodResolver } from '@hookform/resolvers/zod';
import {
  ArrowDropDownOutlined,
  ArrowDropUpOutlined,
  AttachmentOutlined,
  ControlPointOutlined,
  DeleteOutlined,
} from '@mui/icons-material';
import {
  Button,
  Grid,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, useContext, useState } from 'react';
import { FormProvider, type SubmitHandler, useFieldArray, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z } from 'zod';

import { fetchDocumentsChallenge } from '../../../../actions/challenge-action';
import { fetchDocuments } from '../../../../actions/Document';
import { fetchExercises } from '../../../../actions/Exercise';
import { type DocumentHelper } from '../../../../actions/helper';
import MarkDownFieldController from '../../../../components/fields/MarkDownFieldController';
import MultipleFileLoader from '../../../../components/fields/MultipleFileLoader';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import TagFieldController from '../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import { useHelper } from '../../../../store';
import { type ChallengeInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import { zodImplement } from '../../../../utils/Zod';
import DocumentPopover from '../documents/DocumentPopover';
import DocumentType from '../documents/DocumentType';

const useStyles = makeStyles()(() => ({
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  bodyItem: {
    height: '100%',
    fontSize: 13,
  },
  tuple: {
    marginTop: 5,
    paddingTop: 0,
    paddingLeft: 0,
  },
}));

const inlineStylesHeaders: Record<string, CSSProperties> = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  document_name: {
    float: 'left',
    width: '35%',
    fontSize: 12,
    fontWeight: '700',
  },
  document_type: {
    float: 'left',
    width: '20%',
    fontSize: 12,
    fontWeight: '700',
  },
  document_tags: {
    float: 'left',
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
  },
};

const inlineStyles: Record<string, CSSProperties> = {
  document_name: {
    float: 'left',
    width: '35%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  document_type: {
    float: 'left',
    width: '20%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  document_tags: {
    float: 'left',
    width: '30%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

interface Props {
  onSubmit: SubmitHandler<ChallengeInput>;
  handleClose: () => void;
  editing?: boolean;
  challengeId?: string;
  initialValues?: Partial<ChallengeInput>;
}

const ChallengeForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing = false,
  challengeId,
  initialValues = {},
}) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const [documentsSortBy, setDocumentsSortBy] = useState('document_name');
  const [documentsOrderAsc, setDocumentsOrderAsc] = useState(true);

  const { documentsMap } = useHelper((helper: DocumentHelper) => ({ documentsMap: helper.getDocumentsMap() }));

  useDataLoader(() => {
    dispatch(fetchExercises());
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS)) {
      dispatch(fetchDocuments());
    } else if (challengeId) {
      dispatch(fetchDocumentsChallenge(challengeId));
    }
  });

  const flagSchema = z.object({
    flag_type: z.string().min(1, { message: t('This field is required.') }),
    flag_value: z.string().min(1, { message: t('This field is required.') }),
  }).superRefine((flag, ctx) => {
    if (flag.flag_type === 'REGEXP') {
      try {
        RegExp(flag.flag_value);
      } catch {
        ctx.addIssue({
          code: 'custom',
          message: t('Invalid regular expression'),
          path: ['flag_value'],
        });
      }
    }
  });

  // TextFieldController stores its value as a string, even with type="number":
  // coerce it back to a number (or undefined when emptied) before validation.
  const optionalNumber = z.preprocess(
    value => (value === '' || value === null ? undefined : value),
    z.coerce.number().optional(),
  ) as unknown as z.ZodOptional<z.ZodNumber>;

  const schema = zodImplement<ChallengeInput>().with({
    challenge_name: z.string().min(1, { message: t('This field is required.') }),
    challenge_category: z.string().optional(),
    challenge_content: z.string().optional(),
    challenge_score: optionalNumber,
    challenge_max_attempts: optionalNumber,
    challenge_tags: z.array(z.string()).optional(),
    challenge_documents: z.array(z.string()).optional(),
    challenge_flags: z.array(flagSchema).min(1, { message: t('At least one flag is required for a challenge.') }),
  });

  const methods = useForm<ChallengeInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: {
      challenge_name: '',
      challenge_category: '',
      challenge_content: '',
      challenge_tags: [],
      challenge_documents: [],
      challenge_flags: [],
      ...initialValues,
    },
  });

  const {
    control,
    handleSubmit,
    watch,
    setValue,
    formState: { isSubmitting, isDirty },
  } = methods;

  const { fields: flagFields, append: appendFlag, remove: removeFlag } = useFieldArray({
    control,
    name: 'challenge_flags',
  });

  const documents = watch('challenge_documents') ?? [];

  const handleAddDocuments = (updatedDocuments: { document_id: string }[]) => {
    setValue('challenge_documents', updatedDocuments.map(document => document.document_id), { shouldDirty: true });
  };
  const handleRemoveDocument = (docId: string) => {
    setValue('challenge_documents', documents.filter(n => n !== docId), { shouldDirty: true });
  };

  const documentsReverseBy = (field: string) => {
    setDocumentsSortBy(field);
    setDocumentsOrderAsc(!documentsOrderAsc);
  };

  const documentsSortHeader = (field: string, label: string, isSortable: boolean) => {
    const sortComponent = documentsOrderAsc
      ? (
          <ArrowDropDownOutlined style={inlineStylesHeaders.iconSort} />
        )
      : (
          <ArrowDropUpOutlined style={inlineStylesHeaders.iconSort} />
        );
    if (isSortable) {
      return (
        <div
          style={inlineStylesHeaders[field]}
          onClick={() => documentsReverseBy(field)}
        >
          <span>{t(label)}</span>
          {documentsSortBy === field ? sortComponent : ''}
        </div>
      );
    }
    return (
      <div style={inlineStylesHeaders[field]}>
        <span>{t(label)}</span>
      </div>
    );
  };

  const flagTypes = [
    {
      value: 'VALUE',
      label: t('Text'),
    },
    {
      value: 'VALUE_CASE',
      label: t('Text (case-sensitive)'),
    },
    {
      value: 'REGEXP',
      label: t('Regular expression'),
    },
  ];

  // Rendering
  return (
    <FormProvider {...methods}>
      <form id="challengeForm" onSubmit={handleSubmit(onSubmit)}>
        <TextFieldController
          name="challenge_name"
          label={t('Name')}
          required
          style={{ marginTop: 10 }}
        />
        <TextFieldController
          name="challenge_category"
          label={t('Category')}
          style={{ marginTop: 20 }}
        />
        <MarkDownFieldController
          name="challenge_content"
          label={t('Content')}
          style={{ marginTop: 20 }}
          inInject={false}
        />
        <Grid container spacing={3} style={{ marginTop: 0 }}>
          <Grid size={{ xs: 6 }}>
            <TextFieldController
              name="challenge_score"
              type="number"
              label={t('Score')}
            />
          </Grid>
          <Grid size={{ xs: 6 }}>
            <TextFieldController
              name="challenge_max_attempts"
              type="number"
              label={t('Max number of attempts')}
            />
          </Grid>
        </Grid>
        <TagFieldController
          name="challenge_tags"
          label={t('Tags')}
          style={{ marginTop: 20 }}
        />
        <Typography variant="h2" style={{ marginTop: 30 }}>
          {t('Documents')}
        </Typography>
        <List>
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            style={{ paddingTop: 0 }}
            secondaryAction={<>&nbsp;</>}
          >
            <ListItemIcon>
              <span
                style={{
                  padding: '0 8px 0 8px',
                  fontWeight: 700,
                  fontSize: 12,
                }}
              >
                &nbsp;
              </span>
            </ListItemIcon>
            <ListItemText
              primary={(
                <div>
                  {documentsSortHeader('document_name', 'Name', true)}
                  {documentsSortHeader('document_type', 'Type', true)}
                  {documentsSortHeader('document_tags', 'Tags', true)}
                </div>
              )}
            />
          </ListItem>
          {documents
            .map(documentId => documentsMap[documentId])
            .filter(document => document !== undefined)
            .map(document => (
              <ListItem
                key={document.document_id}
                secondaryAction={(
                  <DocumentPopover
                    inline
                    document={document}
                    onRemoveDocument={handleRemoveDocument}
                  />
                )}
              >
                <ListItemButton
                  classes={{ root: classes.item }}
                  divider
                  component="a"
                  href={buildTenantApiPath(`/api/documents/${document.document_id}/file`)}
                >
                  <ListItemIcon>
                    <AttachmentOutlined />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div>
                        <div
                          className={classes.bodyItem}
                          style={inlineStyles.document_name}
                        >
                          {document.document_name}
                        </div>
                        <div
                          className={classes.bodyItem}
                          style={inlineStyles.document_type}
                        >
                          <DocumentType
                            type={document.document_type}
                            variant="list"
                          />
                        </div>
                        <div
                          className={classes.bodyItem}
                          style={inlineStyles.document_tags}
                        >
                          <ItemTags
                            variant="list"
                            tags={document.document_tags}
                          />
                        </div>
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            ))}
          <MultipleFileLoader
            initialDocumentIds={documents}
            handleAddDocuments={handleAddDocuments}
            disabled={!ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS)}
          />
        </List>
        <div style={{ marginTop: 30 }}>
          <Typography variant="h2" style={{ float: 'left' }}>
            {t('Flags')}
          </Typography>
          <IconButton
            onClick={() => appendFlag({
              flag_type: 'VALUE',
              flag_value: '',
            })}
            size="small"
            color="primary"
            style={{
              float: 'left',
              margin: '-8px 0 0 10px',
            }}
          >
            <ControlPointOutlined />
          </IconButton>
          <div className="clearfix" />
          <List>
            {flagFields.map((flagField, index) => (
              <ListItem
                key={flagField.id}
                classes={{ root: classes.tuple }}
                divider={false}
              >
                <SelectFieldController
                  name={`challenge_flags.${index}.flag_type`}
                  label={t('Flag type')}
                  items={flagTypes}
                  style={{ marginRight: theme.spacing(2.5) }}
                />
                <TextFieldController
                  name={`challenge_flags.${index}.flag_value`}
                  label={t('Value')}
                  style={{ marginRight: theme.spacing(2.5) }}
                />
                <IconButton
                  onClick={() => removeFlag(index)}
                  aria-haspopup="true"
                  size="small"
                  color="primary"
                >
                  <DeleteOutlined />
                </IconButton>
              </ListItem>
            ))}
          </List>
          {flagFields.length === 0 && (
            <Typography variant="body2">
              {t('At least one flag is required for a challenge.')}
            </Typography>
          )}
        </div>
        <div style={{
          float: 'right',
          marginTop: 20,
        }}
        >
          <Button
            variant="outlined"
            color="primary"
            onClick={handleClose}
            style={{ marginRight: 10 }}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            type="submit"
            disabled={isSubmitting || !isDirty}
          >
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default ChallengeForm;
