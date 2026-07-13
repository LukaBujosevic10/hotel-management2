import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Guest, Reservation, ReservationStatus, Room } from '../../core/models';
import { errorMessage } from '../../core/api-error';
import { daysBetween, fromApiDate, toApiDate } from '../../core/date.util';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { ReservationDialogComponent } from './reservation-dialog/reservation-dialog.component';
import { ReservationManageDialogComponent } from './reservation-manage-dialog/reservation-manage-dialog.component';

@Component({
  selector: 'app-reservations',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatSortModule, MatButtonModule,
    MatIconModule, MatCardModule, MatMenuModule, MatTooltipModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatDatepickerModule, MatNativeDateModule, PageHeaderComponent
  ],
  templateUrl: './reservations.component.html',
  styleUrls: ['./reservations.component.css']
})
export class ReservationsComponent implements OnInit {

  dataSource = new MatTableDataSource<Reservation>([]);

  @ViewChild(MatSort) set matSort(sort: MatSort) {
    if (sort) this.dataSource.sort = sort;
  }

  guests: Guest[] = [];
  rooms: Room[] = [];

  loading = false;
  loadError = '';

  // filters
  search = '';
  statusFilter = 'ACTIVE';
  stayFrom: Date | null = null;
  stayTo: Date | null = null;

  statuses: ReservationStatus[] =
    ['PENDING', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED'];

  cols = ['id', 'guest', 'room', 'dates', 'nights', 'total', 'status', 'actions'];

  constructor(
    private api: ApiService,
    private dialog: MatDialog,
    private snack: MatSnackBar
  ) {
    this.dataSource.filterPredicate = (r, raw) => {
      const f = JSON.parse(raw) as
          { search: string; status: string; from: string | null; to: string | null };

      // 1. Ako je filter ACTIVE, sakrij CANCELLED i CHECKED_OUT
      if (f.status === 'ACTIVE' && (r.status === 'CANCELLED' || r.status === 'CHECKED_OUT')) {
        return false;
      }

      // 2. Ako filter nije ni ACTIVE ni ALL, zahtevaj tačno poklapanje statusa
      if (f.status !== 'ALL' && f.status !== 'ACTIVE' && r.status !== f.status) {
        return false;
      }

      // Overlap test: keep stays that touch the selected window.
      if (f.from && r.checkOutDate <= f.from) return false;
      if (f.to && r.checkInDate >= f.to) return false;

      if (!f.search) return true;
      const haystack = [
        `#${r.id}`,
        this.guestName(r.guestId),
        this.roomNumber(r.roomId),
        r.checkInDate,
        r.checkOutDate
      ].join(' ').toLowerCase();
      return haystack.includes(f.search);
    };

    this.dataSource.sortingDataAccessor = (r, column) => {
      switch (column) {
        case 'id': return r.id ?? 0;
        case 'guest': return this.guestName(r.guestId).toLowerCase();
        case 'room': return this.roomNumber(r.roomId).toLowerCase();
        case 'dates': return r.checkInDate;
        case 'nights': return this.nights(r);
        case 'total': return r.totalPrice ?? 0;
        case 'status': return r.status ?? '';
        default: return '';
      }
    };
  }

  get visibleCount(): number { return this.dataSource.filteredData.length; }
  get totalCount(): number { return this.dataSource.data.length; }
  get filtersActive(): boolean {
    return !!this.search || this.statusFilter !== 'ACTIVE' || !!this.stayFrom || !!this.stayTo;
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    forkJoin({
      res: this.api.getReservations(),
      guests: this.api.getGuests(),
      rooms: this.api.getRooms()
    }).subscribe({
      next: ({ res, guests, rooms }) => {
        this.guests = guests;
        this.rooms = rooms;
        this.dataSource.data = res;
        this.applyFilters();
        this.loading = false;
      },
      error: err => {
        this.loadError = errorMessage(err, 'Reservations could not be loaded.');
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    this.dataSource.filter = JSON.stringify({
      search: this.search.trim().toLowerCase(),
      status: this.statusFilter,
      from: this.stayFrom ? toApiDate(this.stayFrom) : null,
      to: this.stayTo ? toApiDate(this.stayTo) : null
    });
  }

  clearFilters(): void {
    this.search = '';
    this.statusFilter = 'ACTIVE';
    this.stayFrom = null;
    this.stayTo = null;
    this.applyFilters();
  }

  // ---------------------------------------------------------------- helpers

  guestName(id: number): string {
    const g = this.guests.find(x => x.id === id);
    return g ? `${g.firstName} ${g.lastName}` : `Guest #${id}`;
  }

  roomNumber(id: number): string {
    const r = this.rooms.find(x => x.id === id);
    return r ? `#${r.roomNumber}` : `Room #${id}`;
  }

  nights(r: Reservation): number {
    return daysBetween(fromApiDate(r.checkInDate), fromApiDate(r.checkOutDate));
  }

  /** Which transitions the backend will accept, mirrored so buttons can be disabled. */
  canTransition(r: Reservation, to: ReservationStatus): boolean {
    const from = r.status;
    if (!from || from === to) return false;
    if (from === 'CANCELLED' || from === 'CHECKED_OUT') return false;
    if (to === 'CHECKED_OUT') return from === 'CHECKED_IN';
    return true;
  }

  /** Dates and room may only change before the stay begins. */
  canEdit(r: Reservation): boolean {
    return r.status === 'CONFIRMED' || r.status === 'PENDING';
  }

  // ------------------------------------------------------------------- CRUD

  openCreate(): void {
    this.dialog.open(ReservationDialogComponent, { data: { guests: this.guests } })
      .afterClosed().subscribe(payload => {
        if (!payload) return;
        this.api.createReservation(payload).subscribe({
          next: () => {
            this.snack.open('Reservation booked.', 'OK', { duration: 2500 });
            this.load();
          },
          error: err => this.notifyError(err, 'The reservation could not be created.')
        });
      });
  }

  openEdit(reservation: Reservation): void {
    this.dialog.open(ReservationDialogComponent, {
      data: { guests: this.guests, reservation }
    }).afterClosed().subscribe(payload => {
      if (!payload) return;
      this.api.updateReservation(reservation.id!, payload).subscribe({
        next: () => {
          this.snack.open(`Reservation #${reservation.id} updated.`, 'OK', { duration: 2500 });
          this.load();
        },
        error: err => this.notifyError(err, 'The reservation could not be updated.')
      });
    });
  }

  manage(r: Reservation): void {
    this.dialog.open(ReservationManageDialogComponent, {
      data: { reservationId: r.id! },
      autoFocus: false
    }).afterClosed().subscribe(changed => {
      if (changed) this.load();
    });
  }

  setStatus(r: Reservation, status: ReservationStatus): void {
    this.api.setReservationStatus(r.id!, status).subscribe({
      next: () => {
        this.snack.open(`Reservation #${r.id} is now ${status.replace('_', ' ').toLowerCase()}.`,
          'OK', { duration: 2500 });
        this.load();
      },
      error: err => this.notifyError(err, 'The status could not be changed.')
    });
  }

  private notifyError(err: unknown, fallback: string): void {
    this.snack.open(errorMessage(err, fallback), 'Close', { duration: 7000 });
  }
}
