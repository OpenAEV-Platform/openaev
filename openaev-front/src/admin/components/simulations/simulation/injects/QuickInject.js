import {
  ArrowDropDownOutlined,
  ArrowDropUpOutlined,
  AttachmentOutlined,
  CastForEducationOutlined,
  CloseRounded,
  ControlPointOutlined,
  DeleteOutlined,
  HelpOutlineOutlined,
} from '@mui/icons-material';
import {
  Button,
  FormControlLabel,
  FormGroup,
  IconButton,
  InputLabel,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemSecondaryAction,
  ListItemText,
  MenuItem,
  Slide,
  Switch,
  Typography,
} from '@mui/material';
import arrayMutators from 'final-form-arrays';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component, forwardRef } from 'react';
import { Form } from 'react-final-form';
import { FieldArray } from 'react-final-form-arrays';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { fetchDocuments } from '../../../../../actions/Document';
import { addInjectForExercise } from '../../../../../actions/Inject';
import { storeHelper } from '../../../../../actions/Schema';
import { fetchVariablesForExercise } from '../../../../../actions/variables/variable-actions';
import MultipleFileLoader from '../../../../../components/fields/MultipleFileLoader';
import OldRichTextField from '../../../../../components/fields/OldRichTextField';
import OldSelectField from '../../../../../components/fields/OldSelectField';
import OldSwitchField from '../../../../../components/fields/OldSwitchField';
import OldTextField from '../../../../../components/fields/OldTextField';
import inject18n from '../../../../../components/i18n';
import ItemBoolean from '../../../../../components/ItemBoolean';
import ItemTags from '../../../../../components/ItemTags';
import { secondsFromToNow } from '../../../../../utils/Time.js';
import InjectExpectations from '../../../common/injects/expectations/InjectExpectations';
import InjectAddTeams from '../../../common/injects/form/teams/InjectAddTeams.js';
import DocumentPopover from '../../../components/documents/DocumentPopover';
import DocumentType from '../../../components/documents/DocumentType';
import TeamPopover from '../../../components/teams/TeamPopover';
import AvailableVariablesDialog from '../variables/AvailableVariablesDialog';

export const EMAIL_CONTRACT = '138ad8f8-32f8-4a22-8114-aaa12322bd09';

const Transition = forwardRef((props, ref) => (
  <Slide direction="up" ref={ref} {...props} />
));
Transition.displayName = 'TransitionSlide';

const styles = theme => ({
  header: {
    backgroundColor: theme.palette.background.nav,
    padding: '20px 20px 20px 60px',
  },
  closeButton: {
    position: 'absolute',
    top: 12,
    left: 5,
    color: 'inherit',
  },
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
  tuple: {
    marginTop: 5,
    paddingTop: 0,
    paddingLeft: 0,
  },
  bodyItem: {
    height: '100%',
    fontSize: 13,
  },
  itemIcon: { color: theme.palette.primary.main },
  title: { float: 'left' },
  allTeams: {
    float: 'right',
    marginTop: -7,
  },
  container: { padding: 20 },
});

const inlineStylesHeaders = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  team_name: {
    float: 'left',
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
  },
  team_users_number: {
    float: 'left',
    width: '15%',
    fontSize: 12,
    fontWeight: '700',
  },
  team_enabled: {
    float: 'left',
    width: '15%',
    fontSize: 12,
    fontWeight: '700',
  },
  team_tags: {
    float: 'left',
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
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
  document_attached: {
    float: 'left',
    width: '15%',
    fontSize: 12,
    fontWeight: '700',
  },
};

const inlineStyles = {
  team_name: {
    float: 'left',
    width: '30%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  team_users_number: {
    float: 'left',
    width: '15%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  team_enabled: {
    float: 'left',
    width: '15%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  team_tags: {
    float: 'left',
    width: '30%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
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
  document_attached: {
    float: 'left',
    width: '15%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

class QuickInject extends Component {
  constructor(props) {
    super(props);
    this.state = {
      allTeams: false,
      teamsIds: [],
      documents: [],
      expectations: props.injectorContract.fields.filter(f => f.key === 'expectations').flatMap(f => f.predefinedExpectations) || [],
      teamsSortBy: 'team_name',
      teamsOrderAsc: true,
      documentsSortBy: 'document_name',
      documentsOrderAsc: true,
      openVariables: false,
    };
  }

  contractVariables() {
    const defaultVars = [];
    try {
      return JSON.parse(this.props.injectorContract.injector_contract_content)?.variables ?? defaultVars;
    } catch (_e) {
      return defaultVars;
    }
  }

  componentDidMount() {
    const { exerciseId } = this.props;
    this.props.fetchDocuments();
    this.props.fetchVariablesForExercise(exerciseId);
  }

  toggleAll() {
    this.setState({ allTeams: !this.state.allTeams });
  }

  handleOpenVariables() {
    this.setState({ openVariables: true });
  }

  handleCloseVariables() {
    this.setState({ openVariables: false });
  }

  handleModifyTeams(teamsIds) {
    this.setState({ teamsIds: [...teamsIds] });
  }

  handleRemoveTeam(teamId) {
    this.setState({ teamsIds: this.state.teamsIds.filter(a => a !== teamId) });
  }

  handleAddDocuments(documents) {
    this.setState({ documents });
  }

  handleRemoveDocument(documentId) {
    this.setState({
      documents: this.state.documents.filter(
        d => d.document_id !== documentId,
      ),
    });
  }

  handleExpectations(expectations) {
    this.setState({ expectations });
  }

  toggleAttachment(documentId) {
    this.setState({
      documents: this.state.documents.map(d => (d.document_id === documentId
        ? {
            document_id: d.document_id,
            document_attached: !d.document_attached,
          }
        : d)),
    });
  }

  selectTupleFieldType(name, type) {
    this.setState({ tupleFieldTypes: R.assoc(name, type, this.state.tupleFieldTypes) });
  }

  teamsReverseBy(field) {
    this.setState({
      teamsSortBy: field,
      teamsOrderAsc: !this.state.teamsOrderAsc,
    });
  }

  teamsSortHeader(field, label, isSortable) {
    const { t } = this.props;
    const { teamsSortBy, teamsOrderAsc } = this.state;
    const sortComponent = teamsOrderAsc
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
          onClick={this.teamsReverseBy.bind(this, field)}
        >
          <span>{t(label)}</span>
          {teamsSortBy === field ? sortComponent : ''}
        </div>
      );
    }
    return (
      <div style={inlineStylesHeaders[field]}>
        <span>{t(label)}</span>
      </div>
    );
  }

  documentsReverseBy(field) {
    this.setState({
      documentsSortBy: field,
      documentsOrderAsc: !this.state.documentsOrderAsc,
    });
  }

  documentsSortHeader(field, label, isSortable) {
    const { t } = this.props;
    const { documentsSortBy, documentsOrderAsc } = this.state;
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
          onClick={this.documentsReverseBy.bind(this, field)}
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
  }

  onSubmit(data) {
    const { injectorContract } = this.props;
    const finalData = {};
    const hasExpectations = injectorContract.fields
      .map(f => f.key)
      .includes('expectations');
    if (hasExpectations) {
      finalData.expectations = this.state.expectations;
    }
    injectorContract.fields
      .filter(
        f => !['teams', 'attachments', 'expectations'].includes(
          f.key,
        ),
      )
      .forEach((field) => {
        if (field.type === 'number') {
          finalData[field.key] = parseInt(data[field.key], 10);
        } else if (
          field.type === 'textarea'
          && field.richText
          && data[field.key]
          && data[field.key].length > 0
        ) {
          finalData[field.key] = data[field.key]
            .replaceAll('&lt;/#list&gt;', '</#list>');
        } else if (data[field.key] && field.type === 'tuple') {
          if (field.cardinality && field.cardinality === '1') {
            if (finalData[field.key].type === 'attachment') {
              finalData[field.key] = {
                key: data[field.key].key,
                value: `${field.tupleFilePrefix}${data[field.key].value}`,
              };
            } else {
              finalData[field.key] = R.dissoc('type', data[field.key]);
            }
          } else {
            finalData[field.key] = data[field.key].map((pair) => {
              if (pair.type === 'attachment') {
                return {
                  key: pair.key,
                  value: `${field.tupleFilePrefix}${pair.value}`,
                };
              }
              return R.dissoc('type', pair);
            });
          }
        } else {
          finalData[field.key] = data[field.key];
        }
      });
    const { allTeams, teamsIds, documents } = this.state;
    const injectDependsDuration = secondsFromToNow(
      this.props.exercise.exercise_start_date,
    );
    const values = {
      inject_title: finalData.subject,
      inject_injector_contract: EMAIL_CONTRACT,
      inject_depends_duration:
        injectDependsDuration > 0 ? injectDependsDuration : 0,
      inject_content: finalData,
      inject_all_teams: allTeams,
      inject_teams: teamsIds,
      inject_documents: documents,
    };
    return this.props
      .addInject(this.props.exerciseId, values)
      .then(() => this.props.handleClose());
  }

  validate(values) {
    const { t, injectorContract } = this.props;
    const errors = {};
    if (injectorContract && Array.isArray(injectorContract.fields)) {
      injectorContract.fields
        .filter(
          f => !['teams', 'attachments', 'expectations'].includes(
            f.key,
          ),
        )
        .forEach((field) => {
          const value = values[field.key];
          if (field.mandatory && R.isEmpty(value)) {
            errors[field.key] = t('This field is required.');
          }
        });
    }
    return errors;
  }

  renderFields(renderedFields, values, attachedDocs) {
    const { classes, t, theme } = this.props;
    const { isDisabled } = this.props;

    return (
      <div>
        {renderedFields.map((field, position) => {
          switch (field.type) {
            case 'textarea':
              return field.richText
                ? (
                    <OldRichTextField
                      key={field.key}
                      name={field.key}
                      label={t(field.label)}
                      fullWidth={true}
                      style={{
                        marginTop: theme.spacing(2.5),
                        height: 250,
                      }}
                      disabled={isDisabled}
                    />
                  )
                : (
                    <OldTextField
                      variant="standard"
                      key={field.key}
                      name={field.key}
                      fullWidth={true}
                      multiline={true}
                      rows={10}
                      label={t(field.label)}
                      style={{ marginTop: theme.spacing(2.5) }}
                      disabled={isDisabled}
                    />
                  );
            case 'number':
              return (
                <OldTextField
                  variant="standard"
                  key={field.key}
                  name={field.key}
                  fullWidth={true}
                  type="number"
                  label={t(field.label)}
                  style={{ marginTop: theme.spacing(2.5) }}
                  disabled={isDisabled}
                />
              );
            case 'checkbox':
              return (
                <OldSwitchField
                  key={field.key}
                  name={field.key}
                  label={t(field.label)}
                  style={{ marginTop: position > 0 ? theme.spacing(1.25) : theme.spacing(2.5) }}
                  disabled={isDisabled}
                />
              );
            case 'tuple':
              return (
                <div key={field.key}>
                  <FieldArray name={field.key}>
                    {({ fields }) => (
                      <div>
                        <div style={{ marginTop: theme.spacing(2.5) }}>
                          <InputLabel
                            variant="standard"
                            shrink={true}
                            disabled={isDisabled}
                          >
                            {t(field.label)}
                            {field.cardinality === 'n' && (
                              <IconButton
                                onClick={() => fields.push({
                                  type: 'text',
                                  key: '',
                                  value: '',
                                })}
                                aria-haspopup="true"
                                size="medium"
                                style={{ marginTop: -2 }}
                                color="primary"
                                disabled={isDisabled}
                              >
                                <ControlPointOutlined />
                              </IconButton>
                            )}
                          </InputLabel>
                        </div>
                        <List style={{ marginTop: -theme.spacing(2.5) }}>
                          {fields.map((name, index) => {
                            return (
                              <ListItem
                                key={`${field.key}_list_${index}`}
                                classes={{ root: classes.tuple }}
                                divider={false}
                              >
                                <OldSelectField
                                  variant="standard"
                                  name={`${name}.type`}
                                  fullWidth={true}
                                  label={t('Type')}
                                  style={{ marginRight: theme.spacing(2.5) }}
                                  disabled={isDisabled}
                                >
                                  <MenuItem key="text" value="text">
                                    <ListItemText>{t('Text')}</ListItemText>
                                  </MenuItem>
                                  {field.contractAttachment && (
                                    <MenuItem
                                      key="attachment"
                                      value="attachment"
                                    >
                                      <ListItemText>
                                        {t('Attachment')}
                                      </ListItemText>
                                    </MenuItem>
                                  )}
                                </OldSelectField>
                                <OldTextField
                                  variant="standard"
                                  name={`${name}.key`}
                                  fullWidth={true}
                                  label={t('Key')}
                                  style={{ marginRight: theme.spacing(2.5) }}
                                  disabled={isDisabled}
                                />
                                {values
                                  && values[field.key]
                                  && values[field.key][index]
                                  && values[field.key][index].type
                                  === 'attachment' ? (
                                      <OldSelectField
                                        variant="standard"
                                        name={`${name}.value`}
                                        fullWidth={true}
                                        label={t('Value')}
                                        style={{ marginRight: theme.spacing(2.5) }}
                                      >
                                        {attachedDocs.map(doc => (
                                          <MenuItem
                                            key={doc.document_id}
                                            value={doc.document_id}
                                          >
                                            <ListItemText>
                                              {doc.document_name}
                                            </ListItemText>
                                          </MenuItem>
                                        ))}
                                      </OldSelectField>
                                    ) : (
                                      <OldTextField
                                        variant="standard"
                                        name={`${name}.value`}
                                        fullWidth={true}
                                        label={t('Value')}
                                        style={{ marginRight: theme.spacing(2.5) }}
                                        disabled={isDisabled}
                                      />
                                    )}
                                {field.cardinality === 'n' && (
                                  <IconButton
                                    onClick={() => fields.remove(index)}
                                    aria-haspopup="true"
                                    size="small"
                                    color="primary"
                                    disabled={isDisabled}
                                  >
                                    <DeleteOutlined />
                                  </IconButton>
                                )}
                              </ListItem>
                            );
                          })}
                        </List>
                      </div>
                    )}
                  </FieldArray>
                </div>
              );
            case 'select':
              return field.cardinality === 'n'
                ? (
                    <OldSelectField
                      variant="standard"
                      label={t(field.label)}
                      key={field.key}
                      multiple
                      renderValue={v => v.map(a => field.choices[a]).join(', ')}
                      name={field.key}
                      fullWidth={true}
                      style={{ marginTop: theme.spacing(2.5) }}
                      disabled={isDisabled}
                    >
                      {Object.entries(field.choices)
                        .sort((a, b) => a[1].localeCompare(b[1]))
                        .map(([k, v]) => (
                          <MenuItem key={k} value={k}>
                            <ListItemText>
                              {field.expectation ? t(v || 'Unknown') : v}
                            </ListItemText>
                          </MenuItem>
                        ))}
                    </OldSelectField>
                  )
                : (
                    <OldSelectField
                      variant="standard"
                      label={t(field.label)}
                      key={field.key}
                      renderValue={v => (field.expectation
                        ? t(field.choices[v] || 'Unknown')
                        : field.choices[v])}
                      name={field.key}
                      fullWidth={true}
                      style={{ marginTop: theme.spacing(2.5) }}
                      disabled={isDisabled}
                    >
                      {Object.entries(field.choices)
                        .sort((a, b) => a[1].localeCompare(b[1]))
                        .map(([k, v]) => (
                          <MenuItem key={k} value={k}>
                            <ListItemText>
                              {field.expectation ? t(v || 'Unknown') : v}
                            </ListItemText>
                          </MenuItem>
                        ))}
                    </OldSelectField>
                  );
            case 'dependency-select':
              // eslint-disable-next-line no-case-declarations
              const depValue = values[field.dependencyField];
              // eslint-disable-next-line no-case-declarations
              const choices = field.choices[depValue] ?? {};
              return field.cardinality === 'n'
                ? (
                    <OldSelectField
                      variant="standard"
                      label={t(field.label)}
                      key={field.key}
                      multiple
                      renderValue={v => v.map(a => choices[a]).join(', ')}
                      name={field.key}
                      fullWidth={true}
                      style={{ marginTop: theme.spacing(2.5) }}
                      disabled={isDisabled}
                    >
                      {Object.entries(choices)
                        .sort((a, b) => a[1].localeCompare(b[1]))
                        .map(([k, v]) => (
                          <MenuItem key={k} value={k}>
                            <ListItemText>{v}</ListItemText>
                          </MenuItem>
                        ))}
                    </OldSelectField>
                  )
                : (
                    <OldSelectField
                      variant="standard"
                      label={t(field.label)}
                      key={field.key}
                      renderValue={v => (field.expectation ? t(choices[v] || 'Unknown') : choices[v])}
                      name={field.key}
                      fullWidth={true}
                      style={{ marginTop: theme.spacing(2.5) }}
                      disabled={isDisabled}
                    >
                      {Object.entries(choices)
                        .sort((a, b) => a[1].localeCompare(b[1]))
                        .map(([k, v]) => (
                          <MenuItem key={k} value={k}>
                            <ListItemText>
                              {field.expectation ? t(v || 'Unknown') : v}
                            </ListItemText>
                          </MenuItem>
                        ))}
                    </OldSelectField>
                  );
            default:
              return (
                <OldTextField
                  variant="standard"
                  key={field.key}
                  name={field.key}
                  fullWidth={true}
                  label={t(field.label)}
                  style={{ marginTop: theme.spacing(2.5) }}
                  disabled={isDisabled}
                />
              );
          }
        })}
      </div>
    );
  }

  resetDefaultvalues(setFieldValue, builtInFields) {
    const { injectorContract } = this.props;
    injectorContract.fields
      .filter(f => !builtInFields.includes(f.key) && !f.expectation)
      .forEach((field) => {
        if (field.cardinality && field.cardinality === '1') {
          let defaultValue = R.head(field.defaultValue);
          if (
            field.type === 'textarea'
            && field.richText
            && defaultValue
            && defaultValue.length > 0
          ) {
            defaultValue = defaultValue
              .replaceAll('</#list>', '&lt;/#list&gt;');
          }
          setFieldValue(field.key, defaultValue);
        } else {
          let { defaultValue } = field;
          if (
            field.type === 'textarea'
            && field.richText
            && defaultValue
            && defaultValue.length > 0
          ) {
            defaultValue = defaultValue
              .replaceAll('</#list>', '&lt;/#list&gt;');
          }
          setFieldValue(field.key, defaultValue);
        }
      });
  }

  render() {
    const {
      t,
      theme,
      classes,
      handleClose,
      exerciseId,
      exercise,
      injectorContract,
      teamsMap,
      documentsMap,
      isDisabled,
    } = this.props;
    const {
      allTeams,
      teamsIds,
      documents,
      expectations,
      teamsSortBy,
      teamsOrderAsc,
      documentsSortBy,
      documentsOrderAsc,
      openVariables,
    } = this.state;

    // -- TEAMS --
    const teams = teamsIds
      .map(a => teamsMap[a])
      .filter(a => a !== undefined);
    const sortTeams = R.sortWith(
      teamsOrderAsc
        ? [R.ascend(R.prop(teamsSortBy))]
        : [R.descend(R.prop(teamsSortBy))],
    );
    const sortedTeams = sortTeams(teams);
    const hasTeams = injectorContract.fields
      .map(f => f.key)
      .includes('teams');
    // -- DOCUMENTS --
    const docs = documents
      .map(d => (documentsMap[d.document_id]
        ? {
            ...documentsMap[d.document_id],
            document_attached: d.document_attached,
          }
        : undefined))
      .filter(d => d !== undefined);
    const attachedDocs = docs.filter(n => n.document_attached);
    const sortDocuments = R.sortWith(
      documentsOrderAsc
        ? [R.ascend(R.prop(documentsSortBy))]
        : [R.descend(R.prop(documentsSortBy))],
    );
    const sortedDocuments = sortDocuments(docs);
    const hasAttachments = injectorContract.fields
      .map(f => f.key)
      .includes('attachments');
    // -- EXPECTATIONS --
    const hasExpectations = injectorContract.fields
      .map(f => f.key)
      .includes('expectations');
    const predefinedExpectations = injectorContract.fields.filter(
      f => f.key === 'expectations',
    ).flatMap(f => f.predefinedExpectations);
    const expectationsNotManual = injectorContract.fields.filter(
      f => f.expectation === true,
    );
    const initialValues = {};
    // Enrich initialValues with default contract value
    const builtInFields = [
      'teams',
      'attachments',
      'expectations',
    ];
    injectorContract.fields
      .filter(f => !builtInFields.includes(f.key))
      .forEach((field) => {
        if (!initialValues[field.key]) {
          if (field.cardinality && field.cardinality === '1') {
            initialValues[field.key] = R.head(field.defaultValue);
          } else {
            initialValues[field.key] = field.defaultValue;
          }
        }
      });
    // Specific processing for some fields
    injectorContract.fields
      .filter(f => !builtInFields.includes(f.key))
      .forEach((field) => {
        if (
          field.type === 'textarea'
          && field.richText
          && initialValues[field.key]
          && initialValues[field.key].length > 0
        ) {
          initialValues[field.key] = initialValues[field.key]
            .replaceAll('</#list>', '&lt;/#list&gt;');
        } else if (field.type === 'tuple' && initialValues[field.key]) {
          if (field.cardinality && field.cardinality === '1') {
            if (
              initialValues[field.key].value
              && initialValues[field.key].value.includes(
                `${field.tupleFilePrefix}`,
              )
            ) {
              initialValues[field.key] = {
                type: 'attachment',
                key: initialValues[field.key].key,
                value: initialValues[field.key].value.replace(
                  `${field.tupleFilePrefix}`,
                  '',
                ),
              };
            } else {
              initialValues[field.key] = R.assoc(
                'type',
                'text',
                initialValues[field.key],
              );
            }
          } else {
            initialValues[field.key] = initialValues[field.key].map((pair) => {
              if (
                pair.value
                && pair.value.includes(`${field.tupleFilePrefix}`)
              ) {
                return {
                  type: 'attachment',
                  key: pair.key,
                  value: pair.value.replace(`${field.tupleFilePrefix}`, ''),
                };
              }
              return R.assoc('type', 'text', pair);
            });
          }
        }
      });
    return (
      <div>
        <div className={classes.header}>
          <IconButton
            aria-label="Close"
            className={classes.closeButton}
            onClick={handleClose.bind(this)}
            size="large"
            color="primary"
          >
            <CloseRounded fontSize="small" color="primary" />
          </IconButton>
          <Typography variant="h6" classes={{ root: classes.title }}>
            {t('Quick inject definition')}
          </Typography>
          <div className="clearfix" />
        </div>
        <div className={classes.container}>
          <Form
            keepDirtyOnReinitialize={true}
            initialValues={initialValues}
            onSubmit={this.onSubmit.bind(this)}
            validate={this.validate.bind(this)}
            mutators={{
              ...arrayMutators,
              setValue: ([field, value], state, { changeValue }) => {
                changeValue(state, field, () => value);
              },
            }}
          >
            {({ form, handleSubmit, submitting, values }) => (
              <form id="injectContentForm" onSubmit={handleSubmit}>
                {hasTeams && (
                  <div>
                    <Typography variant="h2" style={{ float: 'left' }}>
                      {t('Targeted teams')}
                    </Typography>
                    <FormGroup
                      row={true}
                      classes={{ root: classes.allTeams }}
                    >
                      <FormControlLabel
                        control={(
                          <Switch
                            checked={allTeams}
                            onChange={this.toggleAll.bind(this)}
                            color="primary"
                            disabled={isDisabled}
                          />
                        )}
                        label={<strong>{t('All teams')}</strong>}
                      />
                    </FormGroup>
                    <div className="clearfix" />
                    <List>
                      <ListItem
                        classes={{ root: classes.itemHead }}
                        divider={false}
                        style={{ paddingTop: 0 }}
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
                              {this.teamsSortHeader(
                                'team_name',
                                'Name',
                                true,
                              )}
                              {this.teamsSortHeader(
                                'team_users_number',
                                'Players',
                                true,
                              )}
                              {this.teamsSortHeader(
                                'team_enabled',
                                'Status',
                                true,
                              )}
                              {this.teamsSortHeader(
                                'team_tags',
                                'Tags',
                                true,
                              )}
                            </div>
                          )}
                        />
                        <ListItemSecondaryAction>
                          &nbsp;
                        </ListItemSecondaryAction>
                      </ListItem>
                      {allTeams ? (
                        <ListItem
                          classes={{ root: classes.item }}
                          divider={true}
                        >
                          <ListItemIcon>
                            <CastForEducationOutlined />
                          </ListItemIcon>
                          <ListItemText
                            primary={(
                              <div>
                                <div
                                  className={classes.bodyItem}
                                  style={inlineStyles.team_name}
                                >
                                  <i>{t('All teams')}</i>
                                </div>
                                <div
                                  className={classes.bodyItem}
                                  style={inlineStyles.team_users_number}
                                >
                                  <strong>
                                    {exercise.exercise_users_number}
                                  </strong>
                                </div>
                                <div
                                  className={classes.bodyItem}
                                  style={inlineStyles.team_enabled}
                                >
                                  <ItemBoolean
                                    status={true}
                                    label={t('Enabled')}
                                    variant="inList"
                                  />
                                </div>
                                <div
                                  className={classes.bodyItem}
                                  style={inlineStyles.team_tags}
                                >
                                  <ItemTags variant="reduced-view" tags={[]} />
                                </div>
                              </div>
                            )}
                          />
                          <ListItemSecondaryAction>
                            &nbsp;
                          </ListItemSecondaryAction>
                        </ListItem>
                      ) : (
                        <div>
                          {sortedTeams.map(team => (
                            <ListItem
                              key={team.team_id}
                              classes={{ root: classes.item }}
                              divider={true}
                              secondaryAction={(
                                <TeamPopover
                                  exerciseId={exerciseId}
                                  team={team}
                                  onRemoveTeam={this.handleRemoveTeam.bind(
                                    this,
                                  )}
                                />
                              )}
                            >
                              <ListItemIcon>
                                <CastForEducationOutlined />
                              </ListItemIcon>
                              <ListItemText
                                primary={(
                                  <div>
                                    <div
                                      className={classes.bodyItem}
                                      style={inlineStyles.team_name}
                                    >
                                      {team.team_name}
                                    </div>
                                    <div
                                      className={classes.bodyItem}
                                      style={inlineStyles.team_users_number}
                                    >
                                      {team.team_users_number}
                                    </div>
                                    <div
                                      className={classes.bodyItem}
                                      style={inlineStyles.team_enabled}
                                    >
                                      <ItemBoolean
                                        status={team.team_enabled}
                                        label={
                                          team.team_enabled
                                            ? t('Enabled')
                                            : t('Disabled')
                                        }
                                        variant="inList"
                                      />
                                    </div>
                                    <div
                                      className={classes.bodyItem}
                                      style={inlineStyles.team_tags}
                                    >
                                      <ItemTags
                                        variant="reduced-view"
                                        tags={team.team_tags}
                                      />
                                    </div>
                                  </div>
                                )}
                              />
                            </ListItem>
                          ))}
                          <InjectAddTeams
                            injectTeamsIds={teamsIds}
                            handleModifyTeams={this.handleModifyTeams.bind(
                              this,
                            )}
                          />
                        </div>
                      )}
                    </List>
                  </div>
                )}
                <div style={{ marginTop: hasTeams ? theme.spacing(2.5) : 0 }}>
                  <div style={{ float: 'left' }}>
                    <Typography variant="h2">{t('Inject data')}</Typography>
                  </div>
                  <div style={{ float: 'right' }}>
                    <Button
                      color="primary"
                      variant="outlined"
                      onClick={this.handleOpenVariables.bind(this)}
                      startIcon={<HelpOutlineOutlined />}
                    >
                      {t('Available variables')}
                    </Button>
                  </div>
                  <div className="clearfix" />
                </div>
                <div style={{ marginTop: -15 }}>
                  {this.renderFields(
                    injectorContract.fields
                      .filter(
                        f => !builtInFields.includes(f.key) && !f.expectation,
                      )
                      .filter((f) => {
                        // Filter display if visible
                        for (
                          let index = 0;
                          index < f.visibleConditionFields.length;
                          index += 1
                        ) {
                          const visibleConditionField = injectorContract.fields.find(g => g.key === f.visibleConditionFields[index]);
                          if (
                            visibleConditionField.type === 'checkbox'
                            && values[visibleConditionField.key] === false
                          ) {
                            return false;
                          }
                          if (
                            visibleConditionField.type === 'select'
                            && !f.linkedValues.includes(values[visibleConditionField.key])
                          ) {
                            return false;
                          }
                        }
                        return true;
                      }),
                    values,
                    attachedDocs,
                  )}
                  <Button
                    color="secondary"
                    variant="outlined"
                    disabled={submitting || isDisabled}
                    onClick={this.resetDefaultvalues.bind(
                      this,
                      form.mutators.setValue,
                      builtInFields,
                    )}
                    style={{ marginTop: 10 }}
                  >
                    {t('Reset to default values')}
                  </Button>
                </div>
                {(hasExpectations || expectationsNotManual.length > 0)
                  && (
                    <>
                      <Typography variant="h2" style={{ marginTop: 30 }}>
                        {t('Inject expectations')}
                      </Typography>
                      {expectationsNotManual.length > 0 && (
                        <div>
                          <div style={{ marginTop: -15 }}>
                            {this.renderFields(
                              expectationsNotManual.filter((f) => {
                                // Filter display if linked fields
                                for (
                                  let index = 0;
                                  index < f.linkedFields.length;
                                  index += 1
                                ) {
                                  const linkedField = f.linkedFields[index];
                                  if (
                                    linkedField.type === 'checkbox'
                                    && values[linkedField.key] === false
                                  ) {
                                    return false;
                                  }
                                  if (
                                    linkedField.type === 'select'
                                    && !f.linkedValues.includes(values[linkedField.key])
                                  ) {
                                    return false;
                                  }
                                }
                                return true;
                              }),
                              values,
                              attachedDocs,
                            )}
                          </div>
                        </div>
                      )}
                      {hasExpectations
                        && (
                          <InjectExpectations
                            predefinedExpectationDatas={predefinedExpectations}
                            expectationDatas={expectations}
                            handleExpectations={this.handleExpectations.bind(this)}
                            isHumanInject={true}
                          />
                        )}
                    </>
                  )}
                <div>
                  <Typography variant="h2" style={{ marginTop: 30 }}>
                    {t('Inject documents')}
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
                            {this.documentsSortHeader(
                              'document_name',
                              'Name',
                              true,
                            )}
                            {this.documentsSortHeader(
                              'document_type',
                              'Type',
                              true,
                            )}
                            {this.documentsSortHeader(
                              'document_tags',
                              'Tags',
                              true,
                            )}
                            {this.documentsSortHeader(
                              'document_attached',
                              'Attachment',
                              true,
                            )}
                          </div>
                        )}
                      />
                    </ListItem>
                    {sortedDocuments.map(document => (
                      <ListItemButton
                        key={document.document_id}
                        classes={{ root: classes.item }}
                        divider={true}
                        component="a"
                        href={`/api/documents/${document.document_id}/file`}
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
                                  variant="reduced-view"
                                  tags={document.document_tags}
                                />
                              </div>
                              <div
                                className={classes.bodyItem}
                                style={inlineStyles.document_attached}
                              >
                                <ItemBoolean
                                  status={
                                    hasAttachments
                                      ? document.document_attached
                                      : null
                                  }
                                  label={
                                    document.document_attached
                                      ? t('Yes')
                                      : t('No')
                                  }
                                  variant="inList"
                                  onClick={(event) => {
                                    event.preventDefault();
                                    this.toggleAttachment(document.document_id);
                                  }}
                                  disabled={!hasAttachments || isDisabled}
                                />
                              </div>
                            </div>
                          )}
                        />
                        <ListItemSecondaryAction>
                          <DocumentPopover
                            inline
                            exerciseId={exerciseId}
                            document={document}
                            onRemoveDocument={this.handleRemoveDocument.bind(
                              this,
                            )}
                            onToggleAttach={
                              hasAttachments
                                ? this.toggleAttachment.bind(this)
                                : null
                            }
                            attached={document.document_attached}
                            disabled={isDisabled}
                          />
                        </ListItemSecondaryAction>
                      </ListItemButton>
                    ))}
                    <MultipleFileLoader
                      exerciseId={exerciseId}
                      initialDocumentIds={documents
                        .filter(a => !a.inject_document_attached)
                        .map(d => d.document_id)}
                      handleAddDocuments={this.handleAddDocuments.bind(this)}
                      hasAttachments={hasAttachments}
                    />
                  </List>
                </div>
                <div style={{
                  float: 'right',
                  margin: '20px 0 20px 0',
                }}
                >
                  <Button
                    variant="contained"
                    color="primary"
                    type="submit"
                    disabled={submitting || isDisabled}
                  >
                    {t('Send')}
                  </Button>
                </div>
              </form>
            )}
          </Form>
        </div>
        <AvailableVariablesDialog
          uriVariable={`/admin/simulations/${exerciseId}/definition`}
          variables={this.props.exerciseVariables}
          open={openVariables}
          handleClose={this.handleCloseVariables.bind(this)}
          variablesFromInjectorContract={this.contractVariables()}
        />
      </div>
    );
  }
}

QuickInject.propTypes = {
  t: PropTypes.func,
  nsdt: PropTypes.func,
  theme: PropTypes.func,
  exerciseId: PropTypes.string,
  exercise: PropTypes.object,
  fetchInjectTeams: PropTypes.func,
  addInject: PropTypes.func,
  handleClose: PropTypes.func,
  injectorContract: PropTypes.object,
  fetchDocuments: PropTypes.func,
};

const select = (state, ownProps) => {
  const helper = storeHelper(state);
  const documentsMap = helper.getDocumentsMap().toJS();
  const teamsMap = helper.getTeamsMap().toJS();
  const { exerciseId } = ownProps;
  const exerciseVariables = helper.getExerciseVariables(exerciseId).toJS();
  return {
    documentsMap,
    teamsMap,
    exerciseVariables,
  };
};

export default R.compose(
  connect(select, {
    fetchDocuments,
    fetchVariablesForExercise,
    addInject: addInjectForExercise,
  }),
  inject18n,
  Component => withStyles(Component, styles),
)(QuickInject);
