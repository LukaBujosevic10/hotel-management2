import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, forkJoin, of } from 'rxjs';
import { ApiService } from '../../../core/api.service';
import { Payment, ReservationDetails, ReservationStatus } from '../../../core/models';
import { errorMessage } from '../../../core/api-error';

/**
 * Everything you need to do with one reservation, in one place: check the guest
 * in or out, cancel it, and settle the payment. Opened by clicking a bar on the
 * occupancy timeline.
 */
@Component({
  selector: 'app-reservation-manage-dialog',
  standalone: true,
  imports: [
    CommonModule, MatDialogModule, MatButtonModule, MatIconModule,
    MatDividerModule, MatProgressBarModule, MatTooltipModule
  ],
  templateUrl: './reservation-manage-dialog.component.html',
  styleUrls: ['./reservation-manage-dialog.component.css']
})
export class ReservationManageDialogComponent implements OnInit {

  details: ReservationDetails | null = null;
  payment: Payment | null = null;

  loading = false;
  working = false;
  error = '';
  notice = '';

  /** Tells the caller whether anything changed, so it can refresh the timeline. */
  private changed = false;

  constructor(
    public ref: MatDialogRef<ReservationManageDialogComponent>,
    private api: ApiService,
    @Inject(MAT_DIALOG_DATA) public data: { reservationId: number }
  ) {
    // Closing via the backdrop or Escape must still report the change.
    ref.disableClose = false;
    ref.beforeClosed().subscribe(result => {
      if (result === undefined) ref.close(this.changed);
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    forkJoin({
      details: this.api.getReservationDetails(this.data.reservationId),
      // A payment may not exist yet if the RabbitMQ consumer has not caught up.
      payment: this.api.getPaymentByReservation(this.data.reservationId)
        .pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ details, payment }) => {
        this.details = details;
        this.payment = payment;
        this.loading = false;
      },
      error: err => {
        this.error = errorMessage(err, 'This reservation could not be loaded.');
        this.loading = false;
      }
    });
  }

  // ------------------------------------------------------------ reservation

  get status(): ReservationStatus | undefined {
    return this.details?.reservation.status;
  }

  /** Mirrors the transition rules enforced by reservation-service. */
  canTransition(to: ReservationStatus): boolean {
    const from = this.status;
    if (!from || from === to || this.working) return false;
    if (from === 'CANCELLED' || from === 'CHECKED_OUT') return false;
    if (to === 'CHECKED_OUT') return from === 'CHECKED_IN';
    return true;
  }

  setStatus(to: ReservationStatus): void {
    this.working = true;
    this.error = '';
    this.notice = '';
    this.api.setReservationStatus(this.data.reservationId, to).subscribe({
      next: () => {
        this.changed = true;
        this.working = false;
        this.notice = `Reservation is now ${to.replace('_', ' ').toLowerCase()}.`;
        this.load();
      },
      error: err => {
        this.working = false;
        this.error = errorMessage(err, 'The status could not be changed.');
      }
    });
  }

  // ---------------------------------------------------------------- payment

  get canPay(): boolean {
    return !!this.payment && this.payment.status === 'PENDING' && !this.working;
  }

  get canRefund(): boolean {
    return !!this.payment && this.payment.status === 'COMPLETED' && !this.working;
  }

  pay(method: 'CARD' | 'CASH'): void {
    if (!this.payment) return;
    this.working = true;
    this.error = '';
    this.notice = '';
    this.api.pay(this.payment.id!, method).subscribe({
      next: () => {
        this.changed = true;
        this.working = false;
        this.notice = `Payment settled by ${method.toLowerCase()}.`;
        this.load();
      },
      error: err => {
        this.working = false;
        this.error = errorMessage(err, 'The payment could not be completed.');
      }
    });
  }

  refund(): void {
    if (!this.payment) return;
    this.working = true;
    this.error = '';
    this.notice = '';
    this.api.refund(this.payment.id!).subscribe({
      next: () => {
        this.changed = true;
        this.working = false;
        this.notice = 'Payment refunded.';
        this.load();
      },
      error: err => {
        this.working = false;
        this.error = errorMessage(err, 'The payment could not be refunded.');
      }
    });
  }

  close(): void {
    this.ref.close(this.changed);
  }
}
