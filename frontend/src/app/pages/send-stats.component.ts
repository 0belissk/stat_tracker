import { Component } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgFor, NgIf } from '@angular/common';

@Component({
  standalone: true,
  selector: 'app-send-stats',
  template: `
  <h2>Send Stats</h2>

  <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
    <div class="field">
      <label for="playerId">Player ID</label>
      <input id="playerId" type="text" formControlName="playerId" required aria-describedby="playerIdHelp">
      <small id="playerIdHelp">Internal ID you use for this player.</small>
      <div class="error" *ngIf="f.playerId.touched && f.playerId.invalid">Player ID is required.</div>
    </div>

    <div class="field">
      <label for="playerEmail">Player Email</label>
      <input id="playerEmail" type="email" formControlName="playerEmail" required aria-describedby="playerEmailHelp">
      <small id="playerEmailHelp">This address will receive the report email.</small>
      <div class="error" *ngIf="f.playerEmail.touched && f.playerEmail.invalid">Valid email is required.</div>
    </div>

    <fieldset class="field" formArrayName="categories">
      <legend>Categories</legend>

      <div *ngFor="let _ of categories.controls; let i = index" [formGroupName]="i" class="row">
        <input type="text" placeholder="Category (e.g., Aces)" formControlName="key" required>
        <input type="text" placeholder="Value (e.g., 5)" formControlName="value" required>
        <button type="button" (click)="removeCategory(i)" aria-label="Remove category">✕</button>
      </div>

      <button type="button" (click)="addCategory()">+ Add category</button>
    </fieldset>

    <button class="primary" type="submit" [disabled]="form.invalid">Preview (no send yet)</button>
  </form>

  <div class="preview" *ngIf="previewText">
    <h3>Plain-text preview</h3>
    <pre>{{ previewText }}</pre>
  </div>
  `,
  styles: [`
    form { max-width: 640px; display:grid; gap:1rem; }
    .row { display:grid; grid-template-columns: 1fr 1fr auto; gap:0.5rem; align-items:center; }
    .field { display:grid; gap:0.4rem; }
    label { font-weight:600; }
    input { padding:0.5rem; border:1px solid #ddd; border-radius:8px; }
    .error { color:#b00020; font-size:0.85rem; }
    button { border:1px solid #ddd; background:#fff; border-radius:8px; padding:0.4rem 0.8rem; cursor:pointer; }
    button.primary { border-color:#333; }
    .preview { margin-top:1.5rem; }
    pre { background:#fafafa; border:1px solid #eee; padding:1rem; border-radius:8px; }
  `],
  imports: [ReactiveFormsModule, NgIf, NgFor]
})
type CategoryFormGroup = FormGroup<{ key: FormControl<string>; value: FormControl<string> }>;

type SendStatsFormGroup = FormGroup<{
  playerId: FormControl<string>;
  playerEmail: FormControl<string>;
  categories: FormArray<CategoryFormGroup>;
}>;

export class SendStatsComponent {
  readonly form: SendStatsFormGroup = this.fb.nonNullable.group({
    playerId: ['', Validators.required],
    playerEmail: ['', [Validators.required, Validators.email]],
    categories: this.fb.array<CategoryFormGroup>([this.createCategory()]),
  });

  previewText = '';

  constructor(private fb: FormBuilder) {}

  get categories(): FormArray<CategoryFormGroup> {
    return this.form.controls.categories;
  }

  get f() {
    return this.form.controls;
  }

  addCategory(): void {
    this.categories.push(this.createCategory());
  }

  removeCategory(index: number): void {
    this.categories.removeAt(index);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      return;
    }

    const { playerId, playerEmail } = this.form.getRawValue();
    const categories = this.categories.getRawValue();

    const lines = [
      `PlayerId: ${playerId}`,
      `PlayerEmail: ${playerEmail}`,
      ...categories.map((category) => `${category.key}: ${category.value}`),
    ];

    this.previewText = lines.join('\n');
  }

  private createCategory(): CategoryFormGroup {
    return this.fb.nonNullable.group({
      key: ['', Validators.required],
      value: ['', Validators.required],
    });
  }
}
