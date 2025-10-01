import { AttachmentOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import type { DocumentHelper } from '../../../../actions/helper';
import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import ItemTags from '../../../../components/ItemTags';
import { useHelper } from '../../../../store';
import type { Challenge, Document } from '../../../../utils/api-types';
import DocumentType from '../../components/documents/DocumentType';

const useStyles = makeStyles()(theme => ({
  itemHead: {
    paddingLeft: theme.spacing(1),
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: theme.spacing(1),
    height: 50,
  },
  bodyItem: {
    height: '100%',
    fontSize: 13,
  },
  documentIcon: {
    paddingTop: theme.spacing(1),
    paddingBottom: theme.spacing(1),
    fontWeight: 700,
    fontSize: 12,
  },
}));

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

interface Props { currentChallenge: Challenge | null }

const ChallengesPreviewDocumentsList: FunctionComponent<Props> = ({ currentChallenge }) => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const headers = [
    {
      field: 'document_name',
      label: 'Name',
      isSortable: true,
      value: (document: Document) => document.document_name,
    },
    {
      field: 'document_type',
      label: 'Type',
      isSortable: true,
      value: (document: Document) => {
        return (
          <DocumentType
            type={document.document_type}
            variant="list"
          />
        );
      },
    },
    {
      field: 'document_tags',
      label: 'Tags',
      isSortable: true,
      value: (document: Document) => {
        return (
          <ItemTags
            variant="list"
            tags={document.document_tags}
          />
        );
      },
    },
  ];

  const { queryableHelpers } = useQueryableWithLocalStorage('documents', buildSearchPagination({ sorts: initSorting('document_name') }));

  const { documentsMap }: { documentsMap: Record<string, Document> } = useHelper((helper: DocumentHelper) => ({ documentsMap: helper.getDocumentsMap() }));

  return (
    <List>
      <ListItem
        classes={{ root: classes.itemHead }}
        divider={false}
        style={{ paddingTop: 0 }}
      >
        <ListItemIcon>
          <span
            className={classes.documentIcon}
          >
                          &nbsp;
          </span>
        </ListItemIcon>
        <ListItemText
          primary={(
            <SortHeadersComponentV2
              headers={headers}
              inlineStylesHeaders={inlineStyles}
              sortHelpers={queryableHelpers.sortHelpers}
            />
          )}
        />
      </ListItem>
      {(currentChallenge?.challenge_documents || []).map(
        (documentId) => {
          const document: Document = documentsMap[documentId] || {};
          return (
            <ListItemButton
              key={document.document_id}
              classes={{ root: classes.item }}
              component={Link}
              divider
              to={`/api/documents/${document.document_id}/file`}
            >
              <ListItemIcon>
                <AttachmentOutlined />
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
                        {header.value?.(document)}
                      </div>
                    ))}
                  </div>
                )}
              />
            </ListItemButton>
          );
        },
      )}
    </List>
  );
};

export default ChallengesPreviewDocumentsList;
