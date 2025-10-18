describe('Coach sends a report', () => {
  it('logs in via guard bypass, sends a report, and sees confirmation', () => {
    cy.intercept('POST', 'http://localhost:8080/api/coach/reports', (req) => {
      expect(req.headers).to.have.property('reportid');
      expect(req.headers['reportid']).to.match(/\d{4}-\d{2}-\d{2}T/);
      req.reply({
        statusCode: 202,
        body: {
          reportId: req.headers['reportid'],
          status: 'QUEUED',
          at: new Date().toISOString(),
        },
      });
    }).as('createReport');

    cy.visit('/send');

    cy.get('#playerId').type('player-42');
    cy.get('#playerEmail').type('player42@example.com');
    cy.get('input[placeholder="Category (e.g., Aces)"]').first().type('Aces');
    cy.get('input[placeholder="Value (e.g., 5)"]').first().type('7');

    cy.contains('button', 'Send report').click();

    cy.wait('@createReport').its('request.body').should('deep.equal', {
      playerId: 'player-42',
      playerEmail: 'player42@example.com',
      categories: { Aces: '7' },
    });

    cy.get('[data-cy="send-status"]').should('contain.text', 'Sent!');
  });
});
