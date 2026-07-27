import { ExpandMoreOutlined, NoteAltOutlined, RateReviewOutlined } from '@mui/icons-material';
import { Box, ButtonBase, Collapse, Paper, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useContext, useEffect, useRef, useState } from 'react';
import { useDispatch } from 'react-redux';
import { useParams } from 'react-router';

import { addLog, fetchLogs } from '../../../../../actions/Log';
import { fetchExerciseObjectives } from '../../../../../actions/Objective';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import { SECTION_LABEL_SX } from '../../../../../components/common/detail/detailStyles';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import { useHelper } from '../../../../../store';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { resolveUserName } from '../../../../../utils/String';
import { PermissionsContext } from '../../../common/Context';
import ExecutionMenu from '../ExecutionMenu';
import LogForm from './LogForm';
import LogPopover from './LogPopover';

// Initials for the author medallion of a log entry ("Alice Turner" -> "AT").
const initialsOf = (name) => {
  return name
    .split(' ')
    .filter(part => part.length > 0)
    .slice(0, 2)
    .map(part => part[0].toUpperCase())
    .join('');
};

const Logs = () => {
  const dispatch = useDispatch();
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  const [openCreateLog, setOpenCreateLog] = useState(false);
  const bottomRef = useRef(null);
  // Fetching data
  const { exerciseId } = useParams();
  const { logs, usersMap } = useHelper((helper) => {
    return {
      logs: helper.getExerciseLogs(exerciseId),
      usersMap: helper.getUsersMap(),
    };
  });
  useDataLoader(() => {
    dispatch(fetchExerciseObjectives(exerciseId));
    dispatch(fetchLogs(exerciseId));
  });
  const scrollToBottom = () => {
    setTimeout(() => {
      bottomRef.current.scrollIntoView({ behavior: 'smooth' });
    }, 400);
  };
  const handleToggleWrite = () => setOpenCreateLog(!openCreateLog);
  useEffect(() => {
    if (openCreateLog) {
      scrollToBottom();
    }
  }, [openCreateLog]);
  const submitCreateLog = (data, action) => {
    const inputValues = R.pipe(
      R.assoc('log_tags', R.pluck('id', data.log_tags)),
    )(data);
    return dispatch(addLog(exerciseId, inputValues)).then((result) => {
      if (result.result) {
        action.reset();
        action.resetFieldState('log_title');
        action.resetFieldState('log_content');
        return handleToggleWrite();
      }
      return result;
    });
  };
  return (
    <div>
      <ExecutionMenu exerciseId={exerciseId} />
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        paddingBottom: 5,
      }}
      >
        {/* Section header: label + write action, same 32px anatomy as the
            other titled sections of the app. */}
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          minHeight: 32,
        }}
        >
          <Typography sx={{
            ...SECTION_LABEL_SX,
            marginBottom: 0,
          }}
          >
            {t('Simulation logs')}
          </Typography>
          <div style={{ flex: 1 }} />
          {permissions.canManage && (
            <ButtonCreate
              label={t('Write an entry')}
              onClick={() => {
                if (!openCreateLog) {
                  handleToggleWrite();
                } else {
                  scrollToBottom();
                }
              }}
            />
          )}
        </Box>

        {logs.length === 0 && (
          <Paper variant="outlined" sx={{ borderRadius: 1 }}>
            <Empty
              icon={NoteAltOutlined}
              message={t('No log entries yet')}
              hint={t('Record observations and decisions taken during the simulation')}
            />
          </Paper>
        )}

        {/* Journal feed */}
        {logs.map((log) => {
          const author = resolveUserName(usersMap[log.log_user] ?? {});
          return (
            <Paper
              key={log.log_id}
              variant="outlined"
              sx={{
                display: 'flex',
                flexDirection: 'column',
                gap: 1,
                padding: 2,
                borderRadius: 1,
              }}
            >
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                flexWrap: 'wrap',
              }}
              >
                {/* Author medallion */}
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 28,
                  height: 28,
                  borderRadius: '50%',
                  flexShrink: 0,
                  fontSize: 11,
                  fontWeight: 600,
                  color: theme.palette.primary.main,
                  backgroundColor: alpha(theme.palette.primary.main, 0.12),
                }}
                >
                  {initialsOf(author) || '?'}
                </Box>
                <Typography sx={{
                  fontSize: 14,
                  fontWeight: 600,
                }}
                >
                  {author}
                </Typography>
                <Typography sx={{
                  fontSize: 12,
                  color: 'text.secondary',
                }}
                >
                  {`${t('added an entry on')} ${nsdt(log.log_created_at)}`}
                </Typography>
                <ItemTags variant="list" tags={log.log_tags} />
                <div style={{ flex: 1 }} />
                {permissions.canManage && <LogPopover exerciseId={exerciseId} log={log} />}
              </Box>
              <Box>
                <Typography sx={{
                  fontSize: 14,
                  fontWeight: 600,
                  marginBottom: 0.5,
                }}
                >
                  {log.log_title}
                </Typography>
                <Typography sx={{
                  fontSize: 13,
                  color: 'text.secondary',
                  whiteSpace: 'pre-wrap',
                }}
                >
                  {log.log_content}
                </Typography>
              </Box>
            </Paper>
          );
        })}

        {/* Composer: collapsed one-line trigger, expands into the entry form. */}
        {permissions.canManage && (
          <Paper variant="outlined" sx={{ borderRadius: 1 }}>
            <ButtonBase
              onClick={handleToggleWrite}
              sx={{
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-start',
                gap: 1.5,
                padding: 2,
              }}
            >
              <RateReviewOutlined fontSize="small" color="primary" />
              <Typography sx={{
                fontSize: 14,
                fontWeight: 500,
              }}
              >
                {t('Write an entry')}
              </Typography>
              <div style={{ flex: 1 }} />
              <ExpandMoreOutlined
                sx={{
                  color: 'text.secondary',
                  transform: openCreateLog ? 'rotate(180deg)' : 'none',
                  transition: 'transform 200ms',
                }}
              />
            </ButtonBase>
            <Collapse in={openCreateLog}>
              <Box sx={{
                padding: 2,
                paddingTop: 0,
              }}
              >
                <LogForm
                  initialValues={{ log_tags: [] }}
                  onSubmit={submitCreateLog}
                  handleClose={() => setOpenCreateLog(false)}
                />
              </Box>
            </Collapse>
          </Paper>
        )}

        <div ref={bottomRef} />
      </Box>
    </div>
  );
};

export default Logs;
