export const environment = {
  production: true,
  apiBaseUrl: '/api',
  cognito: {
    region: 'us-east-1',
    userPoolId: 'us-east-1_7Example',
    clientId: '7exampleclientid1234567890',
    hostedUiDomain: 'vsm-dev-portal.auth.us-east-1.amazoncognito.com',
    redirectUri: 'http://localhost:4200/auth/callback',
    postLogoutRedirectUri: 'http://localhost:4200/',
    scope: 'openid profile email',
  },
};
