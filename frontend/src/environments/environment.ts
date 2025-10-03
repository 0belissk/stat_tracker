export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',
  cognito: {
    region: 'us-east-1',
    userPoolId: 'us-east-1_7Example',
    issuer: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_7Example',
    clientId: '7exampleclientid1234567890',
    hostedUiDomain: 'vsm-dev-portal.auth.us-east-1.amazoncognito.com',
    redirectUri: 'http://localhost:4200/auth/callback',
    postLogoutRedirectUri: 'http://localhost:4200/',
    scope: 'openid profile email',
    responseType: 'code',
    usePkce: true,
    silentRefreshRedirectUri: undefined,
  }
};
