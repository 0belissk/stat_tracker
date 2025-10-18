import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section>
      <h2>Welcome to Volleyball Stats Messenger</h2>
      <p>Use the navbar to navigate. Click "Send Stats" to try the reactive form.</p>

      <form (ngSubmit)="viewReports()" #playerForm="ngForm" class="lookup">
        <label for="playerLookup">Preview a player's report history</label>
        <div class="controls">
          <input
            id="playerLookup"
            name="playerId"
            type="text"
            placeholder="Enter player ID"
            [(ngModel)]="playerId"
            required
          />
          <button type="submit" [disabled]="playerForm.invalid">View reports</button>
        </div>
        <small>We'll take you to the Player Reports list for that ID.</small>
      </form>
    </section>
  `,
  styles: [
    `
      section {
        display: grid;
        gap: 1rem;
        max-width: 520px;
      }
      .lookup {
        display: grid;
        gap: 0.5rem;
        padding: 1rem;
        border: 1px solid #e5e5e5;
        border-radius: 8px;
        background: #fafafa;
      }
      .controls {
        display: flex;
        gap: 0.75rem;
        flex-wrap: wrap;
      }
      input {
        flex: 1 1 220px;
        padding: 0.5rem;
        border: 1px solid #ddd;
        border-radius: 6px;
      }
      button {
        border: 1px solid #333;
        background: #fff;
        border-radius: 6px;
        padding: 0.45rem 0.9rem;
        cursor: pointer;
      }
      button[disabled] {
        cursor: not-allowed;
        opacity: 0.7;
      }
      small {
        color: #555;
      }
    `,
  ],
})
export class HomeComponent {
  playerId = '';

  constructor(private router: Router) {}

  viewReports() {
    const trimmed = this.playerId.trim();
    if (!trimmed) return;
    this.router.navigate(['/players', trimmed, 'reports']);
  }
}
