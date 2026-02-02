import moment, { type MomentInput } from 'moment-timezone';

export const FIVE_SECONDS = 5000;

export const utcDate = (date?: MomentInput) => (date ? moment(date).utc() : moment().utc());

const minTwoDigits = (n: number): string => (n < 10 ? '0' : '') + n;

export const splitDuration = (duration = 0) => {
  let delta = duration;
  const days = Math.floor(delta / 86400);
  delta -= days * 86400;
  const hours = Math.floor(delta / 3600) % 24;
  delta -= hours * 3600;
  const minutes = Math.floor(delta / 60) % 60;
  delta -= minutes * 60;
  const seconds = delta % 60;
  return {
    days: minTwoDigits(days),
    hours: minTwoDigits(hours),
    minutes: minTwoDigits(minutes),
    seconds: minTwoDigits(seconds),
  };
};

export const minutesInFuture = (minutes: number) => moment().utc().add(minutes, 'minutes');

export const calcEndDate = (startDate: string, interval: string): moment.Moment | null => {
  switch (interval) {
    case 'day':
      return moment(startDate).add(1, 'days');
    case 'week':
      return moment(startDate).add(7, 'days');
    case 'month':
      return moment(startDate).add(1, 'months');
    case 'quarter':
      return moment(startDate).add(3, 'months');
    case 'year':
      return moment(startDate).add(12, 'months');
    default:
      return null;
  }
};

export const secondsFromToNow = (date: Date | string | number) => {
  if (!date) {
    return 0;
  }
  // Handle both Date objects and date strings/timestamps
  const timestamp = Math.floor((date instanceof Date ? date.getTime() : new Date(date).getTime()) / 1000);
  const now = Math.floor(Date.now() / 1000);
  return now - timestamp;
};
