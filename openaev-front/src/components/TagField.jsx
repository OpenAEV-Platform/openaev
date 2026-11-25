import { LabelOutlined } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle } from '@mui/material';
import { Component } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { storeHelper } from '../actions/Schema';
import { addTag } from '../actions/tags/tag-action';
import TagForm from '../admin/components/settings/tags/TagForm';
import { validHexColor } from '../utils/Colors';
import { Can } from '../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../utils/permissions/types';
import Autocomplete from './Autocomplete';
import inject18n from './i18n';

const styles = () => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
});

class TagFieldComponent extends Component {
  constructor(props) {
    super(props);
    this.state = {
      tagCreation: false,
      tagInput: '',
    };
  }

  handleOpenTagCreation() {
    this.setState({ tagCreation: true });
  }

  handleCloseTagCreation() {
    this.setState({ tagCreation: false });
  }

  onSubmit(data) {
    const { name, setFieldValue, values } = this.props;
    this.props.addTag(data).then((result) => {
      if (result.result) {
        const newTag = result.entities.tags[result.result];
        // A missing field value counts as an empty list
        const tags = [
          ...(values[name] || []),
          {
            id: newTag.tag_id,
            label: newTag.tag_name,
            color: validHexColor(newTag.tag_color),
          },
        ];
        setFieldValue(name, tags);
        return this.handleCloseTagCreation();
      }
      return result;
    });
  }

  render() {
    const {
      t,
      name,
      tags,
      classes,
      onKeyDown,
      style,
      label,
      placeholder,
      disabled,
    } = this.props;
    const tagsOptions = tags.map(
      n => ({
        id: n.tag_id,
        label: n.tag_name,
        color: validHexColor(n.tag_color),
      }),
    );

    return (
      <>
        <Autocomplete
          variant="standard"
          size="small"
          name={name}
          fullWidth={true}
          multiple={true}
          disabled={disabled}
          label={label}
          placeholder={placeholder}
          options={tagsOptions}
          style={style}
          openCreate={!disabled ? this.handleOpenTagCreation.bind(this) : null}
          onKeyDown={onKeyDown}
          renderOption={option => (
            <>
              <div className={classes.icon} style={{ color: option.color }}>
                <LabelOutlined />
              </div>
              <div className={classes.text}>{option.label}</div>
            </>
          )}
        />
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.TAGS}>
          <Dialog
            open={this.state.tagCreation}
            onClose={this.handleCloseTagCreation.bind(this)}
            slotProps={{ paper: { elevation: 1 } }}
          >
            <DialogTitle>{t('Create a new tag')}</DialogTitle>
            <DialogContent>
              <TagForm
                onSubmit={this.onSubmit.bind(this)}
                handleClose={this.handleCloseTagCreation.bind(this)}
              />
            </DialogContent>
          </Dialog>
        </Can>
      </>
    );
  }
}

const select = (state) => {
  const helper = storeHelper(state);
  return { tags: helper.getTags().toJS() };
};

const TagField = connect(select, { addTag })(inject18n(withStyles(TagFieldComponent, styles)));

export default TagField;
