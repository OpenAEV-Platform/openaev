import { AttachFileRounded, ExpandLess, ExpandMore } from '@mui/icons-material';
import { Avatar, Button, Card, CardContent, CardHeader, IconButton } from '@mui/material';
import { lightBlue } from '@mui/material/colors';
import { useTheme } from '@mui/material/styles';
import purify from 'dompurify';
import parse from 'html-react-parser';
import { useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../components/i18n';
import TruncatedText from '../../../../../components/TruncatedText';
import { resolveUserNames, truncate } from '../../../../../utils/String';

const useStyles = makeStyles()(() => ({
  card: { margin: '0 0 20px 0' },
  cardNested: { margin: '0 0 20px 20px' },
}));

const Communication = (props) => {
  const { communication, communicationUsers, isTopic } = props;
  // Standard hooks
  const [expand, setExpand] = useState(false);
  const expandContent = () => setExpand(!expand);
  const theme = useTheme();
  const { classes } = useStyles();
  const { t, nsdt } = useFormatter();
  const limit = 200;
  let isHtml = false;
  let content;
  const hasAttachment = (communication.communication_attachments ?? []).length > 0;
  if (
    communication.communication_content
    && communication.communication_content.length > 10
  ) {
    content = communication.communication_content;
  } else {
    isHtml = true;
    content = communication.communication_content_html;
  }
  return (
    <Card
      classes={{ root: isTopic ? classes.card : classes.cardNested }}
      raised={false}
      variant="outlined"
    >
      <CardHeader
        avatar={(
          <Avatar sx={{ bgcolor: lightBlue[500] }}>
            {Array.from(communication.communication_from)[0].toUpperCase()}
          </Avatar>
        )}
        action={(
          <div style={{ display: 'flex' }}>
            <IconButton onClick={expandContent} size="small">
              {expand ? <ExpandLess /> : <ExpandMore />}
            </IconButton>
          </div>
        )}
        title={(
          <TruncatedText
            content={communication.communication_subject}
            limit={50}
          />
        )}
        subheader={
          communication.communication_animation ? (
            <span>
              <span
                style={{ color: theme.palette.text.secondary }}
              >
                {t('To')}
              </span>
              &nbsp;
              <span
                style={{ color: theme.palette.secondary.main }}
              >
                <TruncatedText
                  content={resolveUserNames(communicationUsers, true)}
                  limit={60}
                />
              </span>
              ,&nbsp;
              <span
                style={{ color: theme.palette.text.secondary }}
              >
                {t('on')}
                {' '}
                {nsdt(communication.communication_sent_at)}
              </span>
            </span>
          ) : (
            <span>
              {' '}
              <span
                style={{ color: theme.palette.text.secondary }}
              >
                {t('From')}
              </span>
              &nbsp;
              <span
                style={{ color: theme.palette.secondary.main }}
              >
                <TruncatedText
                  content={communication.communication_from}
                  limit={60}
                />
              </span>
              ,&nbsp;
              <span
                style={{ color: theme.palette.text.secondary }}
              >
                {nsdt(communication.communication_sent_at)}
              </span>
            </span>
          )
        }
      />
      <CardContent>
        {isHtml ? (
          <div style={{ marginTop: -5 }}>
            {expand
              ? parse(purify.sanitize(content))
              : parse(purify.sanitize(truncate(content, limit)))}
          </div>
        ) : (
          <div style={{
            marginTop: -5,
            whiteSpace: 'pre-line',
          }}
          >
            {expand ? content : truncate(content, limit)}
          </div>
        )}
        {hasAttachment && (
          <div style={{ marginTop: 10 }}>
            {communication.communication_attachments.map((a) => {
              return (
                <a key={a} href={`/api/communications/attachment?file=${a}`}>
                  <Button
                    variant="contained"
                    style={{
                      marginRight: 10,
                      fontSize: 10,
                    }}
                    startIcon={<AttachFileRounded style={{ fontSize: 14 }} />}
                    color="secondary"
                  >
                    {a.substring(a.lastIndexOf('/') + 1)}
                  </Button>
                </a>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
};

export default Communication;
