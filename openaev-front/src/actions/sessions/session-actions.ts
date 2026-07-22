import { simpleCall, simpleDelCall } from '../../utils/Action';

const SESSION_URI = '/api/sessions';
const PLATFORM_SESSION_URI = '/api/platform-sessions';

// -- TENANT SESSIONS (scoped to the current tenant's users) --

export const fetchSessions = () => {
  return simpleCall(SESSION_URI);
};

export const fetchUserSessions = (userId: string) => {
  return simpleCall(`${SESSION_URI}/user/${userId}`);
};

export const killSession = (sessionId: string) => {
  return simpleDelCall(`${SESSION_URI}/${sessionId}`, {}, true, true);
};

export const killUserSessions = (userId: string) => {
  return simpleDelCall(`${SESSION_URI}/user/${userId}`, {}, true, true);
};

// -- PLATFORM SESSIONS (every user across all tenants, Enterprise Edition) --

export const fetchPlatformSessions = () => {
  return simpleCall(PLATFORM_SESSION_URI);
};

export const killPlatformSession = (sessionId: string) => {
  return simpleDelCall(`${PLATFORM_SESSION_URI}/${sessionId}`, {}, true, true);
};

export const killPlatformUserSessions = (userId: string) => {
  return simpleDelCall(`${PLATFORM_SESSION_URI}/user/${userId}`, {}, true, true);
};
