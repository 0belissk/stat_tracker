export const environment = {
  production: true,
  apiBaseUrl: '/api',
  cognito: {
    region: 'us-east-1',
    userPoolId: 'us-east-1_7Example',
    issuer: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_7Example',
    clientId: '7exampleclientid1234567890',
    hostedUiDomain: 'vsm-dev-portal.auth.us-east-1.amazoncognito.com',
    redirectUri: 'https://vsm-dev-portal.pq.app/auth/callback',
    postLogoutRedirectUri: 'https://vsm-dev-portal.pq.app/',
    scope: 'openid profile email',
    responseType: 'code',
    usePkce: true,
    silentRefreshRedirectUri: undefined,
  }
};
