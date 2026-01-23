const VERY_SUCCESS_COLOR = 'rgb(2,129,8)';
const SUCCESS_COLOR = 'rgb(128,228,133)';
const INTERMEDIATE_COLOR = 'rgb(245, 166, 35)';
const FAILED_COLOR = 'rgb(220, 81, 72)';
const PENDING = 'rgba(248,243,243,0.37)';
const UNKNOWN = 'rgba(73,72,72,0.37)';
export const EMPTY_DATA = 'rgba(128,127,127,0.37)';

// export const colorByAverage = (average: number): string => {
//   switch (true) {
//     case average < 0:
//       return EMPTY_DATA;
//     case average < 25:
//       return FAILED_COLOR;
//     case average < 50:
//       return INTERMEDIATE_COLOR;
//     case (average < 75):
//       return INTERMEDIATE_SUCCESS_COLOR;
//     case average === 100:
//       return VERY_SUCCESS_COLOR;
//     default:
//       return UNKNOWN;
//   }
// };

export const colorByAverage = (average: number): string => {
	switch (true) {
		case average < 0:
			return EMPTY_DATA;
		case average < 25:
			return FAILED_COLOR;
		case average <= 75:
			return INTERMEDIATE_COLOR;
		case average <= 100:
			return SUCCESS_COLOR;
		default:
			return UNKNOWN;
	}
};

export const colorByLabel = (label: string): string => {
  switch (label) {
    case 'success':
      return VERY_SUCCESS_COLOR;
    case 'failed':
      return FAILED_COLOR;
    default:
      return PENDING;
  }
};
