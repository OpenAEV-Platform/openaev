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

export const calcEndDate = (startDate: string, interval: string) => {
  let endDate = null;
  if (interval === 'day') {
    endDate = moment(startDate).add(1, 'days');
  } else if (interval === 'week') {
    endDate = moment(startDate).add(7, 'days');
  } else if (interval === 'month') {
    endDate = moment(startDate).add(1, 'months');
  } else if (interval === 'quarter') {
    endDate = moment(startDate).add(3, 'months');
  } else if (interval === 'year') {
    endDate = moment(startDate).add(12, 'months');
  }
  return endDate;
};

export const secondsFromToNow = (date: Date) => {
  if (!date) {
    return 0;
  }
  const timestamp = Math.floor(new Date(date).getTime() / 1000);
  const now = Math.floor(Date.now() / 1000);
  return now - timestamp;
};
