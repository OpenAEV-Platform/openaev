import { AttachmentOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useEffect, useState } from 'react';
import { useFieldArray, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import type { DocumentHelper } from '../../../../../../actions/helper';
import MultipleFileLoader from '../../../../../../components/fields/MultipleFileLoader';
import { useFormatter } from '../../../../../../components/i18n';
import ItemBoolean from '../../../../../../components/ItemBoolean';
import ItemTags from '../../../../../../components/ItemTags';
import { useHelper } from '../../../../../../store';
import { type Document } from '../../../../../../utils/api-types';
import { Can } from '../../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import DocumentPopover from '../../../../components/documents/DocumentPopover';
import DocumentType from '../../../../components/documents/DocumentType';

const useStyles = makeStyles()(theme => ({
  columns: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr 1fr 1fr',
  },
  bodyItem: {
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    fontSize: theme.typography.h3.fontSize,
  },
  noPadding: { padding: 0 },
}));

interface Props {
  readOnly?: boolean;
  hasAttachments: boolean;
}

const InjectDocumentsList = ({ readOnly, hasAttachments }: Props) => {
  const { t } = useFormatter();
  const { control } = useFormContext();
  const { classes } = useStyles();

  const [sortedDocuments, setSortedDocuments] = useState<(Document & { document_attached: boolean })[]>([]);
  const { documentsMap } = useHelper((helper: DocumentHelper) => ({ documentsMap: helper.getDocumentsMap() }));
  const {
    fields,
    append: appendInjectDocuments,
    remove: removeInjectDocuments,
    update: updateInjectDocuments,
  } = useFieldArray({
    control,
    name: 'inject_documents',
  });

  const injectDocuments = fields as ({
    id: string;
    document_id: string;
    document_attached: boolean;
  })[];

  useEffect(() => {
    const test = (injectDocuments || [])
      .map(d => ({
        ...documentsMap[d.document_id],
        document_attached: d.document_attached,
      }))
      .toSorted((a, b) => (a.document_name ?? '').localeCompare(b.document_name ?? ''));
    setSortedDocuments(test);
  }, [injectDocuments]);

  // -- ACTIONS --

  const toggleAttachment = (documentId: string) => {
    const index = injectDocuments.findIndex(d => d.document_id === documentId);
    if (index !== -1) {
      updateInjectDocuments(index, {
        ...injectDocuments[index],
        document_attached: !injectDocuments[index].document_attached,
      });
    }
  };

  const removeDocuments = (documentId: string) => {
    const index = injectDocuments.findIndex(d => d.document_id === documentId);
    if (index !== -1) {
      removeInjectDocuments(index);
    }
  };

  const addDocuments = (documents: {
    document_id: string;
    document_attached: boolean;
  }[]) => {
    const docIds = injectDocuments.map(d => d.document_id);
    const newDocs = documents.filter(d => !docIds.includes(d.document_id));
    appendInjectDocuments(newDocs);
  };

  return (
    <>
      <List>
        {sortedDocuments.map(document => (
          <ListItem
            key={document.document_id}
            divider
            component="a"
            className={classes.noPadding}
            secondaryAction={(
              <DocumentPopover
                inline
                document={document}
                onRemoveDocument={removeDocuments}
                onToggleAttach={(documentId: string) => hasAttachments ? toggleAttachment(documentId) : undefined}
                attached={document.document_attached}
                disabled={readOnly}
              />
            )}
          >
            <ListItemButton
              href={`/api/documents/${document.document_id}/file`}
            >
              <ListItemIcon>
                <AttachmentOutlined />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div className={classes.columns}>
                    <div className={classes.bodyItem}>
                      {document.document_name}
                    </div>
                    <div className={classes.bodyItem}>
                      <DocumentType
                        type={document.document_type}
                        variant="list"
                      />
                    </div>
                    <div className={classes.bodyItem}>
                      <ItemTags
                        variant="reduced-view"
                        tags={document.document_tags}
                      />
                    </div>
                    <div className={classes.bodyItem}>
                      <ItemBoolean
                        status={hasAttachments && document?.document_attached}
                        label={document.document_attached ? t('Yes') : t('No')}
                        variant="inList"
                        disabled
                      />
                    </div>
                  </div>
                )}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
      <Can I={ACTIONS.ACCESS} a={SUBJECTS.DOCUMENTS}>
        <MultipleFileLoader
          initialDocumentIds={injectDocuments.map(d => d.document_id)}
          handleAddDocuments={addDocuments}
          hasAttachments={hasAttachments}
          disabled={readOnly}
        />
      </Can>
    </>
  );
};

export default InjectDocumentsList;
