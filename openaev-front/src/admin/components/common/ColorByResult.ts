const SUCCESS_100_COLOR = 'rgb(2,129,8)';
const SUCCESS_75_COLOR = 'rgb(128,228,133)';
const SUCCESS_50_COLOR = 'rgb(245, 166, 35)';
const SUCCESS_25_COLOR = 'rgb(220, 81, 72)';
const PENDING = 'rgba(248,243,243,0.37)';
const UNKNOWN = 'rgba(73,72,72,0.37)';
export const EMPTY_DATA = 'rgba(128,127,127,0.37)';

export const colorByAverage = (average: number): string => {
  switch (true) {
    case average < 0:
      return EMPTY_DATA;
    case average < 25:
      return SUCCESS_25_COLOR;
    case average < 50:
      return SUCCESS_50_COLOR;
    case (average < 75):
      return SUCCESS_75_COLOR;
    case average === 100:
      return SUCCESS_100_COLOR;
    default:
      return UNKNOWN;
  }
};

export const colorByLabel = (label: string): string => {
  switch (label) {
    case 'success':
      return SUCCESS_100_COLOR;
    case 'failed':
      return SUCCESS_25_COLOR;
    default:
      return PENDING;
  }
};
