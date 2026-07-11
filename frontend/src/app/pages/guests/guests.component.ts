import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Guest } from '../../core/models';
import { errorMessage } from '../../core/api-error';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { GuestDialogComponent } from './guest-dialog/guest-dialog.component';

@Component({
  selector: 'app-guests',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatSortModule, MatButtonModule,
    MatIconModule, MatCardModule, MatTooltipModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, PageHeaderComponent
  ],
  templateUrl: './guests.component.html',
  styleUrls: ['./guests.component.css']
})
export class GuestsComponent implements OnInit {

  dataSource = new MatTableDataSource<Guest>([]);

  @ViewChild(MatSort) set matSort(sort: MatSort) {
    if (sort) this.dataSource.sort = sort;
  }

  loading = false;
  loadError = '';

  search = '';
  loyaltyFilter = 'ALL';

  constructor(
    private api: ApiService,
    private dialog: MatDialog,
    private snack: MatSnackBar,
    public auth: AuthService
  ) {
    this.dataSource.filterPredicate = (guest, raw) => {
      const f = JSON.parse(raw) as { search: string; loyalty: string };
      const points = guest.loyaltyPoints ?? 0;

      if (f.loyalty === 'NONE' && points > 0) return false;
      if (f.loyalty === 'SOME' && (points <= 0 || points >= 200)) return false;
      if (f.loyalty === 'VIP' && points < 200) return false;

      if (!f.search) return true;
      const haystack = [guest.firstName, guest.lastName, guest.email, guest.phone, guest.documentId]
        .filter(Boolean).join(' ').toLowerCase();
      return haystack.includes(f.search);
    };

    this.dataSource.sortingDataAccessor = (guest, column) => {
      switch (column) {
        case 'name': return `${guest.lastName} ${guest.firstName}`.toLowerCase();
        case 'email': return guest.email.toLowerCase();
        case 'phone': return (guest.phone ?? '').toLowerCase();
        case 'document': return guest.documentId.toLowerCase();
        case 'loyalty': return guest.loyaltyPoints ?? 0;
        default: return '';
      }
    };
  }

  /** Deleting a guest is a manager action (enforced by the gateway). */
  get cols(): string[] {
    const base = ['name', 'email', 'phone', 'document', 'loyalty', 'edit'];
    return this.auth.isManager() ? [...base, 'actions'] : base;
  }

  get visibleCount(): number { return this.dataSource.filteredData.length; }
  get totalCount(): number { return this.dataSource.data.length; }
  get filtersActive(): boolean { return !!this.search || this.loyaltyFilter !== 'ALL'; }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.api.getGuests().subscribe({
      next: guests => {
        this.dataSource.data = guests;
        this.applyFilters();
        this.loading = false;
      },
      error: err => {
        this.loadError = errorMessage(err, 'The guest list could not be loaded.');
        this.loading = false;
      }
    });
  }

  applyFilters(): void {
    this.dataSource.filter = JSON.stringify({
      search: this.search.trim().toLowerCase(),
      loyalty: this.loyaltyFilter
    });
  }

  clearFilters(): void {
    this.search = '';
    this.loyaltyFilter = 'ALL';
    this.applyFilters();
  }

  openCreate(): void {
    this.dialog.open(GuestDialogComponent).afterClosed().subscribe(guest => {
      if (!guest) return;
      this.api.createGuest(guest).subscribe({
        next: created => {
          this.snack.open(`${created.firstName} ${created.lastName} added.`, 'OK', { duration: 2500 });
          this.load();
        },
        error: err => this.notifyError(err, 'The guest could not be created.')
      });
    });
  }

  openEdit(guest: Guest): void {
    this.dialog.open(GuestDialogComponent, { data: { guest } })
      .afterClosed().subscribe(updated => {
        if (!updated) return;
        this.api.updateGuest(guest.id!, updated).subscribe({
          next: saved => {
            this.snack.open(`${saved.firstName} ${saved.lastName} updated.`, 'OK', { duration: 2500 });
            this.load();
          },
          error: err => this.notifyError(err, 'The guest could not be updated.')
        });
      });
  }

  remove(guest: Guest): void {
    if (!this.auth.isManager()) return;
    this.api.deleteGuest(guest.id!).subscribe({
      next: () => {
        this.snack.open(`${guest.firstName} ${guest.lastName} removed.`, 'OK', { duration: 2500 });
        this.load();
      },
      error: err => this.notifyError(err, 'The guest could not be deleted.')
    });
  }

  private notifyError(err: unknown, fallback: string): void {
    this.snack.open(errorMessage(err, fallback), 'Close', { duration: 7000 });
  }
}
