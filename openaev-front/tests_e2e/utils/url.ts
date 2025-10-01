const appUrl = () => {
  return process.env.APP_URL ? process.env.APP_URL : 'http://localhost:3001';
};

export default appUrl;
