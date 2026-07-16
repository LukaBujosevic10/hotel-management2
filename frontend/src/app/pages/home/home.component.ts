import { Component, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialog } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { RoomTimelineComponent } from './room-timeline/room-timeline.component';
import { ReservationManageDialogComponent } from '../reservations/reservation-manage-dialog/reservation-manage-dialog.component';

/**
 * Landing page: the occupancy timeline for the whole hotel.
 * Clicking a bar opens the card for managing that reservation.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule, MatButtonModule, MatIconModule,
    PageHeaderComponent, RoomTimelineComponent
  ],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent {

  @ViewChild(RoomTimelineComponent) timeline?: RoomTimelineComponent;

  constructor(private dialog: MatDialog, public auth: AuthService) {}

  openReservation(reservationId: number): void {
    this.dialog.open(ReservationManageDialogComponent, {
      data: { reservationId },
      autoFocus: false
    }).afterClosed().subscribe(changed => {
      // Only refetch when something actually changed.
      if (changed) this.timeline?.reload();
    });
  }
}
