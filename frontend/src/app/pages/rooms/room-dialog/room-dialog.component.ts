import { Component, Inject, Optional } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { Room } from '../../../core/models';

@Component({
  selector: 'app-room-dialog',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule
  ],
  templateUrl: './room-dialog.component.html',
  styleUrls: ['./room-dialog.component.css']
})
export class RoomDialogComponent {
  types = ['SINGLE', 'DOUBLE', 'TWIN', 'SUITE', 'DELUXE'];

  room: Room = {
    roomNumber: '',
    type: 'SINGLE',
    floor: 1,
    pricePerNight: 0,
    capacity: 1,
    description: ''
  };

  submitted = false;

  /** Set when the dialog was opened to edit an existing room. */
  readonly editing: boolean;

  constructor(
    public ref: MatDialogRef<RoomDialogComponent>,
    @Optional() @Inject(MAT_DIALOG_DATA) data?: { room?: Room }
  ) {
    this.editing = !!data?.room;
    if (data?.room) {
      // Copy, so cancelling leaves the table row untouched.
      this.room = { ...data.room };
    }
  }

  /** Client-side checks that mirror the server rules, so the user sees them instantly. */
  get errors(): Record<string, string> {
    const e: Record<string, string> = {};
    if (!this.room.roomNumber?.trim()) e['roomNumber'] = 'Room number is required.';
    if (!this.room.type) e['type'] = 'Pick a room type.';
    if (this.room.floor == null || this.room.floor < 0) e['floor'] = 'Floor cannot be negative.';
    if (!this.room.pricePerNight || this.room.pricePerNight <= 0) {
      e['pricePerNight'] = 'Price must be greater than 0.';
    }
    if (!this.room.capacity || this.room.capacity < 1) e['capacity'] = 'Capacity must be at least 1.';
    return e;
  }

  get isValid(): boolean {
    return Object.keys(this.errors).length === 0;
  }

  save(): void {
    this.submitted = true;
    if (!this.isValid) return;
    this.ref.close({ ...this.room, roomNumber: this.room.roomNumber.trim() });
  }
}
