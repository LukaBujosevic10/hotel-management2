import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { Payment } from '../../core/models';
import { errorMessage } from '../../core/api-error';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-payments',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatSortModule, MatButtonModule,
    MatIconModule, MatCardModule, MatMenuModule, MatTooltipModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, PageHeaderComponent
  ],
  templateUrl: './payments.component.html',
  styleUrls: ['./payments.component.css']
})
export class PaymentsComponent implements OnInit {

  dataSource = new MatTableDataSource<Payment>([]);

  @ViewChild(MatSort) set matSort(sort: MatSort) {
    if (sort) this.dataSource.sort = sort;
  }

  loading = false;
  loadError = '';

  search = '';
  statusFilter = 'ALL';
  statuses = ['PENDING', 'COMPLETED', 'FAILED', 'REFUNDED'];

  cols = ['id', 'reservation', 'amount', 'method', 'reference', 'status', 'actions'];

  constructor(private api: ApiService, private snack: MatSnackBar) {
    this.dataSource.filterPredicate = (p, raw) => {
      const f = JSON.parse(raw) as { search: string; status: string };
      if (f.status !== 'ALL' && p.status !== f.status) return false;
      if (!f.search) return true;
      const haystack = [`#${p.id}`, `#${p.reservationId}`, p.method, p.transactionRef, String(p.amount)]
        .filter(Boolean).join(' ').toLowerCase();
      return haystack.includes(f.search);
    };

    this.dataSource.sortingDataAccessor = (p, column) => {
      switch (column) {
        case 'id': return p.id ?? 0;
        case 'reservation': return p.reservationId;
        case 'amount': return p.amount;
        case 'tax': return p.taxAmount;
        case 'method': return p.method ?? '';
        case 'reference': return p.transactionRef ?? '';
        case 'status': return p.status;
        default: return '';
      }
    };
  }

  get visibleCount(): number { return this.dataSource.filteredData.length; }
  get totalCount(): number { return this.dataSource.data.length; }
  get filtersActive(): boolean { return !!this.search || this.statusFilter !== 'ALL'; }

  get outstandingTotal(): number {
    return this.dataSource.filteredData
      .filter(p => p.status === 'PENDING')
      .reduce((sum, p) => sum + Number(p.amount ?? 0), 0);
  }

  applyFilters(): void {
    this.dataSource.filter = JSON.stringify({
      search: this.search.trim().toLowerCase(),
      status: this.statusFilter
    });
  }

  clearFilters(): void {
    this.search = '';
    this.statusFilter = 'ALL';
    this.applyFilters();
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.api.getPayments().subscribe({
      next: payments => {
        this.dataSource.data = payments;
        this.applyFilters();
        this.loading = false;
      },
      error: err => {
        this.loadError = errorMessage(err, 'Payments could not be loaded.');
        this.loading = false;
      }
    });
  }

  canPay(p: Payment): boolean {
    return p.status === 'PENDING';
  }

  canRefund(p: Payment): boolean {
    return p.status === 'COMPLETED';
  }

  pay(p: Payment, method: string): void {
    this.api.pay(p.id!, method).subscribe({
      next: () => {
        this.snack.open(`Payment #${p.id} completed.`, 'OK', { duration: 2500 });
        this.load();
      },
      error: err => this.notifyError(err, 'The payment could not be completed.')
    });
  }

  refund(p: Payment): void {
    this.api.refund(p.id!).subscribe({
      next: () => {
        this.snack.open(`Payment #${p.id} refunded.`, 'OK', { duration: 2500 });
        this.load();
      },
      error: err => this.notifyError(err, 'The payment could not be refunded.')
    });
  }

  private notifyError(err: unknown, fallback: string): void {
    this.snack.open(errorMessage(err, fallback), 'Close', { duration: 7000 });
  }
}
