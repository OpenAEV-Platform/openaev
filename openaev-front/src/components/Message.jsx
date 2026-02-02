import { Alert, Snackbar } from '@mui/material';
import * as PropTypes from 'prop-types';
import { head } from 'ramda';
import { Component } from 'react';

import { MESSAGING$ } from '../utils/Environment';
import inject18n from './i18n';

class MessageComponent extends Component {
  constructor(props) {
    super(props);
    this.state = {
      open: false,
      error: false,
      text: '',
    };
  }

  componentDidMount() {
    this.subscription = MESSAGING$.messages.subscribe({
      next: (messages) => {
        const firstMessage = head(messages);
        if (firstMessage) {
          const text = typeof firstMessage.text === 'string'
            ? this.props.t(firstMessage.text)
            : firstMessage.text;
          const error = firstMessage.type === 'error';
          const { sticky } = firstMessage;
          this.setState({
            open: true,
            error,
            text,
            sticky,
          });
        }
      },
    });
  }

  componentWillUnmount() {
    this.subscription.unsubscribe();
  }

  handleCloseMessage(event, reason) {
    if (reason === 'clickaway') return;
    this.setState({ open: false });
  }

  render() {
    const { text, error, open, sticky } = this.state;
    return (
      <Snackbar
        anchorOrigin={{
          vertical: 'top',
          horizontal: 'center',
        }}
        open={open}
        onClose={this.handleCloseMessage.bind(this)}
        autoHideDuration={sticky ? null : 4000}
      >
        {error ? (
          <Alert severity="error" onClose={this.handleCloseMessage.bind(this)}>
            {text.length > 0 && text}
          </Alert>
        ) : (
          <Alert
            severity="success"
            onClose={this.handleCloseMessage.bind(this)}
          >
            {text.length > 0 && text}
          </Alert>
        )}
      </Snackbar>
    );
  }
}

MessageComponent.propTypes = {
  open: PropTypes.bool,
  t: PropTypes.func,
  handleClose: PropTypes.func,
  message: PropTypes.string,
  sticky: PropTypes.bool,
};

const Message = inject18n(MessageComponent);

export default Message;
