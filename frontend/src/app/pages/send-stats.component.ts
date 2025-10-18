import { Component } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { NgFor, NgIf } from '@angular/common';
import { finalize } from 'rxjs/operators';
import { ReportsApiService } from '../services/reports-api.service';
import { ReportRequestPayload, ReportResponseDto } from '../models/report';

@Component({
  selector: 'app-send-stats',
  standalone: true,
  imports: [ReactiveFormsModule, NgIf, NgFor],
  template: `
    <h2>Send Stats</h2>

    <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
      <div class="field">
        <label for="playerId">Player ID</label>
        <input
          id="playerId"
          type="text"
          formControlName="playerId"
          required
          aria-describedby="playerIdHelp"
        />
        <small id="playerIdHelp">Internal ID you use for this player.</small>
        <div class="error" *ngIf="f.playerId.touched && f.playerId.invalid">
          Player ID is required.
        </div>
      </div>

      <div class="field">
        <label for="playerEmail">Player Email</label>
        <input
          id="playerEmail"
          type="email"
          formControlName="playerEmail"
          required
          aria-describedby="playerEmailHelp"
        />
        <small id="playerEmailHelp">This address will receive the report email.</small>
        <div class="error" *ngIf="f.playerEmail.touched && f.playerEmail.invalid">
          Valid email is required.
        </div>
      </div>

      <fieldset class="field" formArrayName="categories">
        <legend>Categories</legend>

        <div *ngFor="let _ of categories.controls; let i = index" [formGroupName]="i" class="row">
          <input type="text" placeholder="Category (e.g., Aces)" formControlName="name" required />
          <input type="text" placeholder="Value (e.g., 5)" formControlName="value" required />
          <button type="button" (click)="removeCategory(i)" aria-label="Remove category">✕</button>
        </div>

        <button type="button" (click)="addCategory()">+ Add category</button>
      </fieldset>

      <button class="primary" type="submit" [disabled]="form.invalid || isSubmitting">
        {{ isSubmitting ? 'Sending…' : 'Send report' }}
      </button>
    </form>

    <p class="status success" *ngIf="status === 'sent' && lastResponse" data-cy="send-status">
      Sent! Confirmation ID: {{ lastResponse.reportId }}
    </p>
    <p class="status error" *ngIf="status === 'error'" data-cy="send-error">
      {{ errorMessage }}
    </p>

    <div class="preview" *ngIf="previewText">
      <h3>Plain-text preview</h3>
      <pre>{{ previewText }}</pre>
    </div>
  `,
  styles: [
    `
      form {
        max-width: 640px;
        display: grid;
        gap: 1rem;
      }
      .row {
        display: grid;
        grid-template-columns: 1fr 1fr auto;
        gap: 0.5rem;
        align-items: center;
      }
      .field {
        display: grid;
        gap: 0.4rem;
      }
      label {
        font-weight: 600;
      }
      input {
        padding: 0.5rem;
        border: 1px solid #ddd;
        border-radius: 8px;
      }
      .error {
        color: #b00020;
        font-size: 0.85rem;
      }
      button {
        border: 1px solid #ddd;
        background: #fff;
        border-radius: 8px;
        padding: 0.4rem 0.8rem;
        cursor: pointer;
      }
      button.primary {
        border-color: #333;
      }
      .status {
        margin-top: 0.5rem;
        font-weight: 600;
      }
      .status.success {
        color: #0a7d32;
      }
      .status.error {
        color: #b00020;
      }
      .preview {
        margin-top: 1.5rem;
      }
      pre {
        background: #fafafa;
        border: 1px solid #eee;
        padding: 1rem;
        border-radius: 8px;
      }
    `,
  ],
})
export class SendStatsComponent {
  form: FormGroup;
  previewText = '';

  status: 'idle' | 'sent' | 'error' = 'idle';
  errorMessage = '';
  isSubmitting = false;
  lastResponse: ReportResponseDto | null = null;

  constructor(
    private fb: FormBuilder,
    private reportsApi: ReportsApiService,
  ) {
    this.form = this.fb.group({
      playerId: ['', Validators.required],
      playerEmail: ['', [Validators.required, Validators.email]],
      categories: this.fb.array<FormGroup>([this.createCategory()]),
    });
  }

  get f(): { [key: string]: AbstractControl } {
    return this.form.controls as { [key: string]: AbstractControl };
  }
  get categories(): FormArray<FormGroup> {
    return this.form.get('categories') as FormArray<FormGroup>;
  }

  createCategory(): FormGroup {
    return this.fb.group({
      name: ['', Validators.required],
      value: ['', Validators.required],
    });
  }

  addCategory() {
    this.categories.push(this.createCategory());
  }
  removeCategory(i: number) {
    this.categories.removeAt(i);
  }

  onSubmit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const playerId = (this.form.get('playerId')?.value ?? '').trim();
    const playerEmail = (this.form.get('playerEmail')?.value ?? '').trim();
    const categories: Record<string, string> = {};
    this.categories.controls.forEach((group: FormGroup) => {
      const name = (group.get('name')?.value ?? '').trim();
      const value = (group.get('value')?.value ?? '').trim();
      if (name) {
        categories[name] = value;
      }
    });

    const lines: string[] = [
      `PlayerId: ${playerId}`,
      `PlayerEmail: ${playerEmail}`,
      ...Object.entries(categories).map(([name, value]) => `${name}: ${value}`),
    ];
    this.previewText = lines.join('\n');

    const payload: ReportRequestPayload = { playerId, playerEmail, categories };

    this.status = 'idle';
    this.errorMessage = '';
    this.isSubmitting = true;
    this.reportsApi
      .sendReport(payload)
      .pipe(finalize(() => (this.isSubmitting = false)))
      .subscribe({
        next: (response) => {
          this.lastResponse = response;
          this.status = 'sent';
        },
        error: () => {
          this.status = 'error';
          this.errorMessage = 'Could not send report. Please try again.';
        },
      });
  }
}
