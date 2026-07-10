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
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Room } from '../../core/models';
import { errorMessage } from '../../core/api-error';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { RoomDialogComponent } from './room-dialog/room-dialog.component';

/**
 * Room inventory.
 *
 * Managers manage the inventory. Receptionists get a read-only view: the
 * gateway rejects room writes for them anyway, so the UI does not offer
 * actions that would only come back as 403.
 */
@Component({
  selector: 'app-rooms',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatSortModule, MatButtonModule,
    MatIconModule, MatCardModule, MatMenuModule, MatTooltipModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, PageHeaderComponent
  ],
  templateUrl: './rooms.component.html',
  styleUrls: ['./rooms.component.css']
})
export class RoomsComponent implements OnInit {

  dataSource = new MatTableDataSource<Room>([]);

  /** The table lives inside *ngIf, so bind the sort through a setter. */
  @ViewChild(MatSort) set matSort(sort: MatSort) {
    if (sort) this.dataSource.sort = sort;
  }

  loading = false;
  loadError = '';

  // filters
  search = '';
  typeFilter = 'ALL';
  statusFilter = 'ALL';
  types: string[] = [];

  constructor(
    private api: ApiService,
    private dialog: MatDialog,
    private snack: MatSnackBar,
    public auth: AuthService
  ) {
    this.dataSource.filterPredicate = (room, raw) => {
      const f = JSON.parse(raw) as { search: string; type: string; status: string };

      if (f.type !== 'ALL' && room.type !== f.type) return false;

      const status = room.status === 'MAINTENANCE' ? 'MAINTENANCE' : 'AVAILABLE';
      if (f.status !== 'ALL' && status !== f.status) return false;

      if (!f.search) return true;
      const haystack = [room.roomNumber, room.type, room.description, String(room.floor)]
        .filter(Boolean).join(' ').toLowerCase();
      return haystack.includes(f.search);
    };

    // Sort by the numeric value of a room number, not its string form,
    // so #9 comes before #10.
    this.dataSource.sortingDataAccessor = (room, column) => {
      switch (column) {
        case 'roomNumber': {
          const n = Number(room.roomNumber);
          return isNaN(n) ? room.roomNumber.toLowerCase() : n;
        }
        case 'price': return room.pricePerNight;
        case 'status': return room.status === 'MAINTENANCE' ? 1 : 0;
        case 'type': return room.type;
        case 'floor': return room.floor;
        case 'capacity': return room.capacity;
        default: return '';
      }
    };
  }

  get cols(): string[] {
    const base = ['roomNumber', 'type', 'floor', 'price', 'capacity', 'status'];
    return this.auth.isManager() ? [...base, 'actions'] : base;
  }

  get visibleCount(): number {
    return this.dataSource.filteredData.length;
  }

  get totalCount(): number {
    return this.dataSource.data.length;
  }

  get filtersActive(): boolean {
    return !!this.search || this.typeFilter !== 'ALL' || this.statusFilter !== 'ALL';
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.api.getRooms().subscribe({
      next: rooms => {
        this.dataSource.data = rooms;
        this.types = [...new Set(rooms.map(r => r.type))].sort();
        this.applyFilters();
        this.loading = false;
      },
      error: err => {
        this.loadError = errorMessage(err, 'The room list could not be loaded.');
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    this.dataSource.filter = JSON.stringify({
      search: this.search.trim().toLowerCase(),
      type: this.typeFilter,
      status: this.statusFilter
    });
  }

  clearFilters(): void {
    this.search = '';
    this.typeFilter = 'ALL';
    this.statusFilter = 'ALL';
    this.applyFilters();
  }

  // ------------------------------------------------------------------ CRUD

  openCreate(): void {
    if (!this.auth.isManager()) return;
    this.dialog.open(RoomDialogComponent).afterClosed().subscribe(room => {
      if (!room) return;
      this.api.createRoom(room).subscribe({
        next: () => {
          this.snack.open(`Room ${room.roomNumber} created.`, 'OK', { duration: 2500 });
          this.load();
        },
        error: err => this.notifyError(err, 'The room could not be created.')
      });
    });
  }

  openEdit(room: Room): void {
    if (!this.auth.isManager()) return;
    this.dialog.open(RoomDialogComponent, { data: { room } })
      .afterClosed().subscribe(updated => {
        if (!updated) return;
        this.api.updateRoom(room.id!, updated).subscribe({
          next: () => {
            this.snack.open(`Room ${updated.roomNumber} updated.`, 'OK', { duration: 2500 });
            this.load();
          },
          error: err => this.notifyError(err, 'The room could not be updated.')
        });
      });
  }

  setStatus(room: Room, status: 'AVAILABLE' | 'MAINTENANCE'): void {
    if (!this.auth.isManager()) return;
    this.api.setRoomStatus(room.id!, status).subscribe({
      next: () => {
        this.snack.open(status === 'MAINTENANCE'
          ? `Room ${room.roomNumber} marked as under maintenance.`
          : `Room ${room.roomNumber} is bookable again.`, 'OK', { duration: 2500 });
        this.load();
      },
      error: err => this.notifyError(err, 'The room status could not be changed.')
    });
  }

  remove(room: Room): void {
    if (!this.auth.isManager()) return;
    this.api.deleteRoom(room.id!).subscribe({
      next: () => {
        this.snack.open(`Room ${room.roomNumber} deleted.`, 'OK', { duration: 2500 });
        this.load();
      },
      error: err => this.notifyError(err, 'The room could not be deleted.')
    });
  }

  private notifyError(err: unknown, fallback: string): void {
    this.snack.open(errorMessage(err, fallback), 'Close', { duration: 7000 });
  }
}
