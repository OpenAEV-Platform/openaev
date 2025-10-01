const ChannelColor = (type: string | undefined) => {
  switch (type) {
    case 'newspaper':
      return '#3f51b5';
    case 'microblogging':
      return '#00bcd4';
    case 'tv':
      return '#ff9800';
    default:
      return '#ef41e1';
  }
};

export default ChannelColor;
