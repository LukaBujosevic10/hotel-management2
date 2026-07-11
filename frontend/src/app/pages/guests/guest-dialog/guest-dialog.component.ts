import { Component, Inject, Optional } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { Guest } from '../../../core/models';

@Component({
  selector: 'app-guest-dialog',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatButtonModule
  ],
  templateUrl: './guest-dialog.component.html',
  styleUrls: ['./guest-dialog.component.css']
})
export class GuestDialogComponent {

  guest: Guest = {
    firstName: '', lastName: '', email: '', phone: '', documentId: ''
  };

  submitted = false;

  /** Set when the dialog was opened to edit an existing guest. */
  readonly editing: boolean;

  constructor(
    public ref: MatDialogRef<GuestDialogComponent>,
    @Optional() @Inject(MAT_DIALOG_DATA) data?: { guest?: Guest }
  ) {
    this.editing = !!data?.guest;
    if (data?.guest) {
      this.guest = { ...data.guest };
    }
  }

  /** Mirrors the server rules so problems show up before the request is sent. */
  get errors(): Record<string, string> {
    const e: Record<string, string> = {};
    if (!this.guest.firstName?.trim()) e['firstName'] = 'First name is required.';
    if (!this.guest.lastName?.trim()) e['lastName'] = 'Last name is required.';
    if (!this.guest.email?.trim()) {
      e['email'] = 'E-mail is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.guest.email.trim())) {
      e['email'] = 'Enter a valid e-mail address, e.g. name@example.com.';
    }
    if (this.guest.phone?.trim() && !/^[+0-9 ()-]{6,20}$/.test(this.guest.phone.trim())) {
      e['phone'] = 'Use 6-20 digits, spaces, +, - or ().';
    }
    if (!this.guest.documentId?.trim()) e['documentId'] = 'Document ID is required.';
    return e;
  }

  get isValid(): boolean {
    return Object.keys(this.errors).length === 0;
  }

  save(): void {
    this.submitted = true;
    if (!this.isValid) return;
    this.ref.close({
      firstName: this.guest.firstName.trim(),
      lastName: this.guest.lastName.trim(),
      email: this.guest.email.trim(),
      phone: this.guest.phone?.trim() ?? '',
      documentId: this.guest.documentId.trim()
    });
  }
}
