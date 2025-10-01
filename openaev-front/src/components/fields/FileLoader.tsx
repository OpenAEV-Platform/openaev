import { AttachmentOutlined, ControlPointOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText, Typography } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchDocumentFromSecurityPlatform } from '../../actions/assets/securityPlatform-actions';
import { fetchDocuments } from '../../actions/Document';
import { type DocumentHelper } from '../../actions/helper';
import DocumentType from '../../admin/components/components/documents/DocumentType';
import { useHelper } from '../../store';
import { type RawDocument } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../utils/permissions/PermissionsProvider';
import RestrictionAccess from '../../utils/permissions/RestrictionAccess';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import ButtonPopover, { type PopoverEntry } from '../common/ButtonPopover';
import { useFormatter } from '../i18n';
import ItemTags from '../ItemTags';
import FileTransferDialog from './FileTransferDialog';

const useStyles = makeStyles()(theme => ({
  bodyItem: {
    height: '100%',
    fontSize: 13,
  },
  item: {
    'paddingLeft': 10,
    'height': 50,
    'cursor': 'pointer',
    '&:hover': { backgroundColor: theme.palette.action?.hover },
  },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
  title: {
    fontSize: 12,
    color: theme.palette.text?.secondary,
    fontWeight: 500,
    marginTop: 20,
    marginBottom: 5,
  },
  errorText: { color: theme.palette.error.main },
  errorMessage: {
    color: theme.palette.error.main,
    fontSize: '0.75rem',
    marginTop: 4,
  },
  errorDivider: { borderColor: theme.palette.error.main },
}));

const inlineStyles: Record<string, CSSProperties> = {
  document_name: {
    float: 'left',
    width: '35%',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  document_type: {
    float: 'left',
    width: '20%',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  document_tags: {
    float: 'left',
    width: '30%',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

interface Props {
  initialValue?: {
    id?: string;
    label?: string;
  };
  extensions?: string[];
  label: string;
  name: string;
  setFieldValue: (field: string, value: {
    id?: string;
    label?: string;
  } | undefined) => void;
  /* For mandatory fields */
  InputLabelProps?: { required: boolean };
  error?: boolean;
  parentResourceType?: string;
  parentResourceId?: string;
}

const FileLoader: FunctionComponent<Props> = ({
  initialValue,
  extensions = [],
  label,
  name,
  setFieldValue,
  InputLabelProps,
  error,
  parentResourceType,
  parentResourceId,
}) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const [open, setOpen] = useState(false);
  const [selectedDocument, setSelectedDocument] = useState<RawDocument | undefined>(undefined);
  // Check if selectedDocument resulted from a remove action or an interaction with the File loader dialog
  const [firstInteraction, setFirstInteraction] = useState(false);

  // Fetching data
  const { documents }: { documents: [RawDocument] } = useHelper((helper: DocumentHelper) => ({ documents: helper.getDocuments() }));

  useDataLoader(() => {
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS)) {
      dispatch(fetchDocuments());
    } else if (parentResourceType == 'security_platform' && parentResourceId != null) {
      dispatch(fetchDocumentFromSecurityPlatform(parentResourceId));
    }
  });

  useEffect(() => {
    if (initialValue?.id && documents.length > 0) {
      const resolvedDocument = documents.find(doc => doc.document_id === initialValue.id);
      if (resolvedDocument) {
        setSelectedDocument(resolvedDocument);
      }
    }
  }, [documents]);

  useEffect(() => {
    if (firstInteraction) {
      if (selectedDocument) {
        setFieldValue(name, {
          id: selectedDocument.document_id,
          label: selectedDocument.document_name,
        });
      } else {
        setFieldValue(name, undefined);
      }
    }
  }, [selectedDocument]);

  const handleOpen = () => {
    setFirstInteraction(true);
    setOpen(true);
  };

  // Actions
  const handleRemove = () => {
    setFirstInteraction(true);
    setSelectedDocument(undefined);
  };

  const handleDownload = (documentId: string | undefined) => {
    setFirstInteraction(true);
    if (documentId) {
      window.location.href = `/api/documents/${documentId}/file`;
    }
  };

  // Button Popover entries
  const entries: PopoverEntry[] = [
    {
      label: 'Update',
      action: handleOpen,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.DOCUMENTS),
    },
    {
      label: 'Download',
      action: () => handleDownload(selectedDocument?.document_id),
      userRight: true,
    },
    {
      label: 'Remove',
      action: handleRemove,
      userRight: true,
    },
  ];

  return (
    <>
      <Typography
        className={`${classes.title} ${InputLabelProps?.required && error ? classes.errorText : ''}`}
      >
        {label}
        {InputLabelProps?.required && <span className={error ? classes.errorText : ''}> *</span>}
      </Typography>
      <List style={{
        marginTop: 0,
        paddingTop: 0,
      }}
      >
        {!selectedDocument && (
          ability.can(ACTIONS.MANAGE, SUBJECTS.DOCUMENTS)
            ? (
                <ListItem
                  className={`${classes.item} ${InputLabelProps?.required && error ? classes.errorDivider : ''}`}
                  divider
                  onClick={handleOpen}
                  color="primary"
                >
                  <ListItemIcon color="primary">
                    <ControlPointOutlined color="primary" />
                  </ListItemIcon>
                  <ListItemText
                    primary="Add document"
                    classes={{ primary: classes.text }}
                  />
                </ListItem>
              )
            : <RestrictionAccess restrictedField="documents" />
        )}
        {InputLabelProps?.required && error && (
          <Typography className={classes.errorMessage}>
            {t('Should not be empty')}
          </Typography>
        )}
        {selectedDocument && (
          <ListItem
            classes={{ root: classes.item }}
            key={selectedDocument.document_id}
            divider
            onClick={handleOpen}
          >
            <ListItemIcon>
              <AttachmentOutlined />
            </ListItemIcon>
            <ListItemText
              primary={(
                <>
                  <div className={classes.bodyItem} style={inlineStyles.document_name}>
                    {selectedDocument.document_name}
                  </div>
                  <div className={classes.bodyItem} style={inlineStyles.document_type}>
                    <DocumentType type={selectedDocument.document_type} variant="list" />
                  </div>
                  <div className={classes.bodyItem} style={inlineStyles.document_tags}>
                    <ItemTags
                      variant="reduced-view"
                      tags={selectedDocument.document_tags}
                    />
                  </div>
                </>
              )}
            />
            <ListItemSecondaryAction>
              <ButtonPopover
                entries={entries}
                variant="icon"
              />
            </ListItemSecondaryAction>
          </ListItem>
        )}
      </List>
      {open && (
        <FileTransferDialog
          label={t('Add document')}
          open={open}
          setOpen={setOpen}
          onAddDocument={setSelectedDocument}
          extensions={extensions}
        >
        </FileTransferDialog>
      )}
    </>
  );
};

export default FileLoader;
