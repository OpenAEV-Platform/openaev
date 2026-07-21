import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { useEffect, useRef, useState } from 'react';

import { logout } from '../actions/Application';
import { simpleCall } from '../utils/Action';
import { useAppDispatch } from '../utils/hooks';
import useAuth from '../utils/hooks/useAuth';
import { useFormatter } from './i18n';

// localStorage key shared across browser tabs so the idle countdown stays in sync between them
const SESSION_LOCKOUT_TRACKER_KEY = 'session_lockout_tracker';
// Once the screen is locked, the user has at most 5 minutes to react before being logged out
const LOCK_COUNTDOWN_SECONDS = 300;
const ONE_SECOND = 1000;

interface TrackerData { startDateEpoch: number }

const writeIdleStart = (): number => {
  const data: TrackerData = { startDateEpoch: Date.now() };
  localStorage.setItem(SESSION_LOCKOUT_TRACKER_KEY, JSON.stringify(data));
  return data.startDateEpoch;
};

const readIdleStart = (): number => {
  const raw = localStorage.getItem(SESSION_LOCKOUT_TRACKER_KEY);
  if (raw) {
    try {
      const data = JSON.parse(raw) as TrackerData;
      if (typeof data.startDateEpoch === 'number') {
        return data.startDateEpoch;
      }
    } catch {
      // Corrupted tracker: fall through and reinitialize it
    }
  }
  return writeIdleStart();
};

/**
 * Locks the screen behind a blocking dialog after platform_session_idle_timeout of inactivity
 * (any click resets the countdown, synced across tabs through localStorage) and logs the user
 * out when the lock countdown expires or when platform_session_timeout is exceeded.
 * Only mounted when the idle timeout is enabled (> 0) on the platform.
 */
const TimeoutLock = () => {
  const { t, du } = useFormatter();
  const dispatch = useAppDispatch();
  const { settings } = useAuth();
  // Server values are in milliseconds, this component works in seconds
  const idleLimit = Math.floor((settings.platform_session_idle_timeout ?? 0) / ONE_SECOND);
  const sessionLimit = Math.floor((settings.platform_session_timeout ?? 0) / ONE_SECOND);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [lockRemaining, setLockRemaining] = useState(LOCK_COUNTDOWN_SECONDS);
  const dialogOpenRef = useRef(false);
  dialogOpenRef.current = dialogOpen;

  const handleLogout = async () => {
    try {
      await dispatch(logout());
    } finally {
      window.location.href = '/';
    }
  };

  const handleContinue = () => {
    setDialogOpen(false);
    writeIdleStart();
    // Lightweight authenticated call to touch (and thus extend) the server session
    simpleCall('/api/me', undefined, false).catch(() => undefined);
  };

  useEffect(() => {
    if (idleLimit <= 0) {
      return undefined;
    }
    // A stale tracker (e.g. left over from a previous session) would lock the screen right at
    // login: loading the authenticated app is an activity in itself, so restart the countdown
    if ((Date.now() - readIdleStart()) / ONE_SECOND >= idleLimit) {
      writeIdleStart();
    }
    const onActivity = () => {
      // Clicks never reset the countdown once the screen is locked
      if (!dialogOpenRef.current) {
        writeIdleStart();
      }
    };
    document.body.addEventListener('click', onActivity);
    const onTick = () => {
      const elapsed = (Date.now() - readIdleStart()) / ONE_SECOND;
      if (elapsed < idleLimit) {
        // Handles "Continue session" clicked in another tab
        setDialogOpen(false);
        return;
      }
      // Locked: remaining time before automatic logout, capped by the rolling session timeout
      let remaining = LOCK_COUNTDOWN_SECONDS - (elapsed - idleLimit);
      if (sessionLimit > 0) {
        remaining = Math.min(remaining, sessionLimit - elapsed);
      }
      if (remaining <= 0) {
        handleLogout();
        return;
      }
      setLockRemaining(Math.ceil(remaining));
      setDialogOpen(true);
    };
    onTick();
    const interval = setInterval(onTick, ONE_SECOND);
    return () => {
      document.body.removeEventListener('click', onActivity);
      clearInterval(interval);
    };
  }, [idleLimit, sessionLimit]);

  return (
    <Dialog
      open={dialogOpen}
      disableEscapeKeyDown
      slotProps={{ backdrop: { sx: { backdropFilter: 'blur(15px)' } } }}
    >
      <DialogTitle>{t('Session timeout')}</DialogTitle>
      <DialogContent>
        <DialogContentText>
          {t('Automatic logout in')}
          &nbsp;
          <strong>{du(lockRemaining * ONE_SECOND)}</strong>
        </DialogContentText>
        <DialogContentText>
          {t('You will be automatically logged out at end of the timer.')}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button color="primary" onClick={handleLogout}>{t('Logout')}</Button>
        <Button color="secondary" onClick={handleContinue}>{t('Continue session')}</Button>
      </DialogActions>
    </Dialog>
  );
};

export default TimeoutLock;
