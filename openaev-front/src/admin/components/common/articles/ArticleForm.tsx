import { ArrowDropDownOutlined, ArrowDropUpOutlined, AttachmentOutlined } from '@mui/icons-material';
import {
  Box, Button,
  Grid, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useContext, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { type DocumentHelper } from '../../../../actions/helper';
import AutocompleteField from '../../../../components/fields/AutocompleteField';
import MarkDownFieldController from '../../../../components/fields/MarkDownFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import { useHelper } from '../../../../store';
import { type Channel, type Document } from '../../../../utils/api-types';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { type AppAbility } from '../../../../utils/permissions/ability';
import { AbilityContext, Can } from '../../../../utils/permissions/permissionsContext';
import RestrictionAccess from '../../../../utils/permissions/RestrictionAccess';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import ChannelIcon from '../../components/channels/ChannelIcon';
import DocumentPopover from '../../components/documents/DocumentPopover';
import DocumentType from '../../components/documents/DocumentType';
import { ArticleContext } from '../Context';
import ArticleAddDocuments, { type ChannelType } from './ArticleAddDocuments';
import { type ArticleFormInput } from './ArticleUtils';

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
  autoCompleteIndicator: { '& .MuiAutocomplete-clearIndicator': { display: 'none' } },
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

interface ArticleFormProps {
  handleClose: () => void;
  editing: boolean;
}

export interface ArticleChannel {
  id: string;
  label: string;
  type: string;
}

interface ArticleData {
  channels: Channel[];
  documentsMap: Record<string, Document>;
}

const ArticleForm = ({
  handleClose,
  editing,
}: ArticleFormProps) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { classes } = useStyles();
  const ability: AppAbility = useContext(AbilityContext);
  // Channels and documents are scoped to the current screen (simulation, scenario…):
  // always go through ArticleContext instead of the global endpoints.
  const { fetchChannels, fetchDocuments } = useContext(ArticleContext);

  const { control, watch, setValue, formState: { isSubmitting } } = useFormContext<ArticleFormInput>();
  const [documentsSortBy, setDocumentsSortBy] = useState('document_name');
  const [documentsOrderAsc, setDocumentsOrderAsc] = useState(true);
  const watchChannelId = watch('article_channel');
  const currentDocuments = watch('article_documents') || [];

  // Fetching data
  const { channels, documentsMap } = useHelper((helper: ChannelsHelper & DocumentHelper) => {
    const data: ArticleData = {
      channels: helper.getChannels(),
      documentsMap: helper.getDocumentsMap(),
    };
    return data;
  });

  useDataLoader(() => {
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.CHANNELS)) {
      fetchChannels();
    }
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.DOCUMENTS)) {
      fetchDocuments();
    }
  });

  const handleAddDocuments = (docsIds: string[]) => {
    setValue('article_documents', [...currentDocuments, ...docsIds], {
      shouldValidate: true,
      shouldDirty: true,
    });
  };
  const handleRemoveDocument = (docId: string) => {
    setValue('article_documents', currentDocuments.filter(id => id !== docId));
  };

  // Preparing data
  const sortedChannels: ArticleChannel[] = [...channels]
    .sort((a, b) => (a.channel_name || '').localeCompare(b.channel_name || ''))
    .map(n => ({
      id: n.channel_id,
      label: n.channel_name,
      type: n.channel_type,
    }));

  const selectedChannel = sortedChannels.find(c => c.id === watchChannelId);

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

  // Rendering
  return (
    <>
      <Typography
        variant="h2"
        style={{
          marginTop: 0,
          marginBottom: theme.spacing(2),
        }}
      >
        {t('Information')}
      </Typography>
      <Can not I={ACTIONS.ACCESS} a={SUBJECTS.CHANNELS}>
        <RestrictionAccess restrictedField="channels" />
      </Can>
      <Controller
        name="article_channel"
        control={control}
        render={({ field: { onChange, value }, fieldState }) => {
          return (
            <AutocompleteField
              variant="standard"
              label={t('Channel')}
              multiple={false}
              options={sortedChannels}
              value={value}
              onChange={onChange}
              onInputChange={() => {
              }}
              style={{ width: '100%' }}
              error={!!fieldState.error}
              renderOption={(renderProps, option) => (
                <Box component="li" {...renderProps} key={option.id}>
                  <div className={classes.icon}>
                    <ChannelIcon type={(option as ArticleChannel).type} />
                  </div>
                  <div className={classes.text}>{t(option.label)}</div>
                </Box>
              )}
              className={classes.autoCompleteIndicator}
            />
          );
        }}
      />
      <TextFieldController
        name="article_name"
        label={t('Title')}
        required
        style={{ marginTop: 20 }}
      />

      <TextFieldController
        name="article_author"
        label={t('Author')}
        style={{ marginTop: 20 }}
      />

      <MarkDownFieldController
        name="article_content"
        label={t('Content')}
        style={{ marginTop: 20 }}
        disabled={isSubmitting}
        askAi
        inInject={false}
        inArticle
      />

      <Grid container spacing={3} style={{ marginTop: 20 }}>
        <Grid size={{ xs: 4 }}>
          <TextFieldController
            name="article_comments"
            label={t('Comments')}
            type="number"
          />
        </Grid>
        <Grid size={{ xs: 4 }}>
          <TextFieldController
            name="article_shares"
            label={t('Shares')}
            type="number"
          />
        </Grid>
        <Grid size={{ xs: 4 }}>
          <TextFieldController
            name="article_likes"
            label={t('Likes')}
            type="number"
          />
        </Grid>
      </Grid>

      <Typography variant="h2" style={{ marginTop: 30 }}>
        {t('Documents')}
      </Typography>

      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
          secondaryAction={(<>&nbsp;</>)}
        >
          <ListItemIcon>
            <span style={{
              padding: '0 8px 0 8px',
              fontWeight: 700,
              fontSize: 12,
            }}
            >
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

        {currentDocuments.map(documentId => documentsMap[documentId]).filter(document => document !== undefined).map((document) => {
          return (
            <ListItem
              key={document.document_id}
              divider
              secondaryAction={(
                <DocumentPopover
                  inline
                  document={document}
                  onRemoveDocument={handleRemoveDocument}
                />
              )}
            >
              <ListItemButton
                key={document.document_id}
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
          );
        })}

        {selectedChannel?.type && (
          <Can I={ACTIONS.ACCESS} a={SUBJECTS.DOCUMENTS}>
            <ArticleAddDocuments
              articleDocumentsIds={currentDocuments}
              handleAddDocuments={handleAddDocuments}
              channelType={selectedChannel.type as ChannelType}
            />
          </Can>
        )}
      </List>

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
          disabled={isSubmitting}
        >
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </>
  );
};

export default ArticleForm;
