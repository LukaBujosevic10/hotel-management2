import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../../core/api.service';
import { Guest, Reservation, Room } from '../../../core/models';
import { fromApiDate } from '../../../core/date.util';
import { errorMessage } from '../../../core/api-error';
import { addDays, daysBetween, toApiDate, today } from '../../../core/date.util';
import { GuestDialogComponent } from '../../guests/guest-dialog/guest-dialog.component';

/**
 * Booking flow in two steps.
 *
 * Step 1 asks for the period, because "is this room free?" is only a
 * meaningful question once the dates are known. Step 2 then offers exactly
 * the rooms the backend confirmed are free for that period.
 */
@Component({
  selector: 'app-reservation-dialog',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatButtonModule, MatIconModule, MatDatepickerModule,
    MatNativeDateModule, MatProgressSpinnerModule, MatRadioModule, MatTooltipModule
  ],
  templateUrl: './reservation-dialog.component.html',
  styleUrls: ['./reservation-dialog.component.css']
})
export class ReservationDialogComponent {

  step: 1 | 2 = 1;

  /** True when an existing reservation is being edited rather than created. */
  readonly editing: boolean = false;
  private originalRoomId: number | null = null;

  // step 1
  checkIn: Date | null = null;
  checkOut: Date | null = null;
  numberOfGuests = 1;
  /** An existing stay may already have started, so do not force it into the future. */
  readonly minCheckIn = today();
  dateError = '';
  searching = false;

  // step 2
  availableRooms: Room[] = [];
  selectedRoomId: number | null = null;
  guestId: number | null = null;
  bookError = '';
  addingGuest = false;

  constructor(
    public ref: MatDialogRef<ReservationDialogComponent>,
    private api: ApiService,
    private dialog: MatDialog,
    @Inject(MAT_DIALOG_DATA) public data: { guests: Guest[]; reservation?: Reservation }
  ) {
    const existing = data.reservation;
    this.editing = !!existing;
    if (existing) {
      this.checkIn = fromApiDate(existing.checkInDate);
      this.checkOut = fromApiDate(existing.checkOutDate);
      this.numberOfGuests = existing.numberOfGuests;
      this.guestId = existing.guestId;
      this.originalRoomId = existing.roomId;
    }
  }

  /**
   * Registers a guest without leaving the booking flow. The new guest is added
   * to the list in place and selected straight away, so the receptionist does
   * not have to abandon the reservation and start over from the Guests page.
   */
  addGuest(): void {
    this.dialog.open(GuestDialogComponent, { autoFocus: true })
      .afterClosed().subscribe(newGuest => {
        if (!newGuest) return;
        this.addingGuest = true;
        this.bookError = '';
        this.api.createGuest(newGuest).subscribe({
          next: created => {
            this.data.guests = [...this.data.guests, created];
            this.guestId = created.id ?? null;
            this.addingGuest = false;
          },
          error: err => {
            this.addingGuest = false;
            this.bookError = errorMessage(err, 'The guest could not be created.');
          }
        });
      });
  }

  // --------------------------------------------------------------- step 1

  get minCheckOut(): Date {
    return this.checkIn ? addDays(this.checkIn, 1) : addDays(this.minCheckIn, 1);
  }

  get nights(): number {
    if (!this.checkIn || !this.checkOut) return 0;
    return daysBetween(this.checkIn, this.checkOut);
  }

  /** Keeps check-out valid whenever check-in moves. */
  onCheckInChange(): void {
    if (this.checkIn && this.checkOut && this.checkOut <= this.checkIn) {
      this.checkOut = addDays(this.checkIn, 1);
    }
    this.dateError = '';
  }

  private validateDates(): boolean {
    if (!this.checkIn) {
      this.dateError = 'Pick a check-in date.';
      return false;
    }
    if (!this.checkOut) {
      this.dateError = 'Pick a check-out date.';
      return false;
    }
    if (this.nights < 1) {
      this.dateError = 'The check-out date must be after the check-in date.';
      return false;
    }
    if (daysBetween(today(), this.checkIn) < 0) {
      this.dateError = 'The check-in date cannot be in the past.';
      return false;
    }
    if (!this.numberOfGuests || this.numberOfGuests < 1) {
      this.dateError = 'There must be at least one guest.';
      return false;
    }
    this.dateError = '';
    return true;
  }

  searchRooms(): void {
    if (!this.validateDates()) return;

    this.searching = true;
    this.bookError = '';
    this.api.getAvailableRooms(
      toApiDate(this.checkIn!), toApiDate(this.checkOut!), this.numberOfGuests,
      this.data.reservation?.id
    ).subscribe({
      next: rooms => {
        this.availableRooms = rooms;
        // Keep the current room selected when editing, if it is still on offer.
        const keep = rooms.find(r => r.id === this.originalRoomId);
        this.selectedRoomId = keep ? keep.id! : (rooms.length ? rooms[0].id! : null);
        this.searching = false;
        this.step = 2;
      },
      error: err => {
        this.searching = false;
        this.dateError = errorMessage(err, 'Available rooms could not be loaded.');
      }
    });
  }

  // --------------------------------------------------------------- step 2

  backToDates(): void {
    this.step = 1;
    this.bookError = '';
  }

  get selectedRoom(): Room | undefined {
    return this.availableRooms.find(r => r.id === this.selectedRoomId);
  }

  get estimatedTotal(): number {
    const room = this.selectedRoom;
    return room ? room.pricePerNight * this.nights : 0;
  }

  get periodLabel(): string {
    if (!this.checkIn || !this.checkOut) return '';
    const fmt = (d: Date) => d.toLocaleDateString(undefined, {
      day: '2-digit', month: 'short', year: 'numeric'
    });
    return `${fmt(this.checkIn)} \u2192 ${fmt(this.checkOut)} \u00B7 ${this.nights} night(s)`;
  }

  book(): void {
    if (!this.guestId) {
      this.bookError = 'Pick the guest this reservation is for.';
      return;
    }
    if (!this.selectedRoomId) {
      this.bookError = 'Pick one of the available rooms.';
      return;
    }

    const payload: Reservation = {
      id: this.data.reservation?.id,
      guestId: this.guestId,
      roomId: this.selectedRoomId,
      checkInDate: toApiDate(this.checkIn!),
      checkOutDate: toApiDate(this.checkOut!),
      numberOfGuests: this.numberOfGuests
    };
    this.ref.close(payload);
  }

  /** Marks the room the reservation already uses, so the change is obvious. */
  isCurrentRoom(room: Room): boolean {
    return this.editing && room.id === this.originalRoomId;
  }

  trackByRoom = (_: number, room: Room) => room.id;
}
