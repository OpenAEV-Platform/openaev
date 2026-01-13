
export const colorByAverage= (average: number): string => {
  switch (true) {
    case average === - 1:
      return 'rgba(128,127,127,0.37)';
    case average < 25:
      return 'rgb(220, 81, 72)';
    case average < 50:
      return 'rgb(245, 166, 35)';
    case (average < 75):
      return 'rgb(107, 235, 112)';
    case average === 100:
      return 'rgb(2,80,6)';
    default:
      return 'rgba(73,72,72,0.37)';
  }
};

export const colorByLabel = (label: string): string => {
  switch (label){
    case 'success':
      return 'rgb(2,129,8)';
    case 'failed':
      return 'rgb(220, 81, 72)';
    default:
      return 'rgba(248,243,243,0.37)';
  }
};