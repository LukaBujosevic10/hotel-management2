import { AfterViewInit, Component, ElementRef, EventEmitter, OnInit, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../../../core/api.service';
import { ReservationStatus, Room, TimelineResponse } from '../../../core/models';
import { errorMessage } from '../../../core/api-error';
import { addDays, daysBetween, fromApiDate, isSameDay, isWeekend, toApiDate, today } from '../../../core/date.util';

interface DayColumn {
  date: Date;
  dayOfMonth: number;
  weekdayShort: string;
  monthShort: string;
  firstOfMonth: boolean;
  weekend: boolean;
  isToday: boolean;
}

interface Bar {
  reservationId: number;
  label: string;
  tooltip: string;
  status: ReservationStatus;
  left: number;
  width: number;
  clippedStart: boolean;
  clippedEnd: boolean;
}

interface Row {
  room: Room;
  bars: Bar[];
  bookedNights: number;
}

/**
 * Endless occupancy chart.
 *
 * A window of days is rendered around an anchor date and can be dragged left or
 * right with the mouse. Whenever the viewport approaches either edge the window
 * grows in that direction and the data is refetched, so panning never hits a
 * wall. The window is trimmed on the opposite side to keep both the DOM and the
 * server query bounded (the backend caps a timeline query at one year).
 */
@Component({
  selector: 'app-room-timeline',
  standalone: true,
  imports: [
    CommonModule, MatCardModule, MatButtonModule, MatIconModule,
    MatTooltipModule, MatButtonToggleModule, MatProgressBarModule
  ],
  templateUrl: './room-timeline.component.html',
  styleUrls: ['./room-timeline.component.css']
})
export class RoomTimelineComponent implements OnInit, AfterViewInit {

  @Output() reservationSelected = new EventEmitter<number>();

  @ViewChild('scroller') scroller?: ElementRef<HTMLDivElement>;

  /** Width of the sticky room-label column; must match the CSS. */
  private readonly labelWidth = 148;

  /** How far today sits from the left edge when centred: one fifth. */
  private readonly todayAnchorRatio = 0.2;

  /** Days added each time the user pans close to an edge. */
  private readonly chunkDays = 30;

  /** Upper bound on the loaded window (backend rejects more than a year). */
  private readonly maxDays = 300;

  /** Distance from an edge, in pixels, that triggers loading more days. */
  private readonly edgeThreshold = 320;

  zoomOptions = [
    { label: 'S', width: 26 },
    { label: 'M', width: 42 },
    { label: 'L', width: 64 }
  ];
  dayWidth = 42;

  windowStart: Date = addDays(today(), -30);
  windowDays = 90;

  days: DayColumn[] = [];
  rows: Row[] = [];

  loading = false;
  error = '';

  dragging = false;

  private dragStartX = 0;
  private dragStartScroll = 0;
  private dragMoved = false;
  private extending = false;
  private pendingCentre = false;
  private lastResponse: TimelineResponse | null = null;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.days = this.buildDays();
    this.fetch(() => this.centreOnToday());
  }

  ngAfterViewInit(): void {
    if (this.pendingCentre) this.centreOnToday();
  }

  // ------------------------------------------------------------ positioning

  get trackWidth(): number {
    return this.windowDays * this.dayWidth;
  }

  get rangeLabel(): string {
    const end = addDays(this.windowStart, this.windowDays - 1);
    const fmt = (d: Date) => d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
    return `${fmt(this.windowStart)} \u2013 ${fmt(end)} ${end.getFullYear()}`;
  }

  /** Puts today one fifth in from the left edge of the visible track. */
  centreOnToday(): void {
    const el = this.scroller?.nativeElement;
    if (!el) { this.pendingCentre = true; return; }
    this.pendingCentre = false;

    const offsetDays = daysBetween(this.windowStart, today());
    const visibleTrack = Math.max(el.clientWidth - this.labelWidth, 0);
    const target = offsetDays * this.dayWidth - visibleTrack * this.todayAnchorRatio;
    el.scrollLeft = Math.max(target, 0);
  }

  goToToday(): void {
    this.windowStart = addDays(today(), -30);
    this.windowDays = 90;
    this.days = this.buildDays();
    this.fetch(() => this.centreOnToday());
  }

  setZoom(width: number): void {
    this.dayWidth = width;
    this.rebuildBars();
    // Day widths changed, so any remembered scroll offset is meaningless.
    setTimeout(() => this.centreOnToday());
  }

  /** Arrow buttons: pan by a week without dragging. */
  pan(days: number): void {
    const el = this.scroller?.nativeElement;
    if (!el) return;
    el.scrollBy({ left: days * this.dayWidth, behavior: 'smooth' });
  }

  // ------------------------------------------------------------ drag to pan

  onPointerDown(event: PointerEvent): void {
    if (event.button !== 0) return;
    const el = this.scroller?.nativeElement;
    if (!el) return;
    this.dragging = true;
    this.dragMoved = false;
    this.dragStartX = event.clientX;
    this.dragStartScroll = el.scrollLeft;
  }

  onPointerMove(event: PointerEvent): void {
    if (!this.dragging) return;
    const el = this.scroller?.nativeElement;
    if (!el) return;
    const dx = event.clientX - this.dragStartX;
    if (Math.abs(dx) > 4) this.dragMoved = true;
    el.scrollLeft = this.dragStartScroll - dx;
  }

  onPointerUp(): void {
    this.dragging = false;
    // The click that follows a drag must be swallowed first, then re-enable.
    setTimeout(() => { this.dragMoved = false; }, 0);
  }

  // -------------------------------------------------------- endless window

  onScroll(): void {
    const el = this.scroller?.nativeElement;
    if (!el || this.extending || this.loading) return;

    if (el.scrollLeft < this.edgeThreshold) {
      this.extend('start');
    } else if (el.scrollLeft + el.clientWidth > el.scrollWidth - this.edgeThreshold) {
      this.extend('end');
    }
  }

  private extend(side: 'start' | 'end'): void {
    const el = this.scroller?.nativeElement;
    if (!el) return;
    this.extending = true;

    const shiftPx = this.chunkDays * this.dayWidth;
    let scrollAdjust = 0;

    if (side === 'start') {
      this.windowStart = addDays(this.windowStart, -this.chunkDays);
      this.windowDays += this.chunkDays;
      scrollAdjust = shiftPx;                    // keep the view visually still
      if (this.windowDays > this.maxDays) {
        this.windowDays -= this.chunkDays;       // trim the far end
      }
    } else {
      this.windowDays += this.chunkDays;
      if (this.windowDays > this.maxDays) {
        this.windowStart = addDays(this.windowStart, this.chunkDays);
        this.windowDays -= this.chunkDays;
        scrollAdjust = -shiftPx;
      }
    }

    this.days = this.buildDays();
    this.fetch(
      () => {
        if (scrollAdjust) el.scrollLeft += scrollAdjust;
        this.extending = false;
      },
      () => { this.extending = false; }
    );
  }

  // ------------------------------------------------------------------ data

  /** Public entry point used by the dashboard after a reservation changes. */
  reload(): void {
    this.fetch();
  }

  private fetch(onDone?: () => void, onError?: () => void): void {
    this.loading = true;
    this.error = '';

    const from = toApiDate(this.windowStart);
    const to = toApiDate(addDays(this.windowStart, this.windowDays));

    this.api.getTimeline(from, to).subscribe({
      next: res => {
        this.lastResponse = res;
        this.rows = this.buildRows(res);
        this.loading = false;
        // Wait for the new width to be laid out before touching scrollLeft.
        setTimeout(() => onDone?.());
      },
      error: err => {
        this.rows = [];
        this.error = errorMessage(err, 'The occupancy timeline could not be loaded.');
        this.loading = false;
        onError?.();
      }
    });
  }

  /** Re-lays out the existing data, e.g. after a zoom change. */
  private rebuildBars(): void {
    this.days = this.buildDays();
    if (this.lastResponse) this.rows = this.buildRows(this.lastResponse);
  }

  private buildDays(): DayColumn[] {
    const now = today();
    const columns: DayColumn[] = [];
    for (let i = 0; i < this.windowDays; i++) {
      const date = addDays(this.windowStart, i);
      columns.push({
        date,
        dayOfMonth: date.getDate(),
        weekdayShort: date.toLocaleDateString(undefined, { weekday: 'narrow' }),
        monthShort: date.toLocaleDateString(undefined, { month: 'short' }),
        firstOfMonth: date.getDate() === 1 || i === 0,
        weekend: isWeekend(date),
        isToday: isSameDay(date, now)
      });
    }
    return columns;
  }

  private buildRows(res: TimelineResponse): Row[] {
    const windowEnd = addDays(this.windowStart, this.windowDays);

    return res.rooms.map(rt => {
      const bars: Bar[] = [];
      let bookedNights = 0;

      for (const entry of rt.entries) {
        const checkIn = fromApiDate(entry.checkInDate);
        const checkOut = fromApiDate(entry.checkOutDate);

        const visibleStart = checkIn < this.windowStart ? this.windowStart : checkIn;
        const visibleEnd = checkOut > windowEnd ? windowEnd : checkOut;

        const startOffset = daysBetween(this.windowStart, visibleStart);
        const nights = daysBetween(visibleStart, visibleEnd);
        if (nights <= 0) continue;
        bookedNights += nights;

        bars.push({
          reservationId: entry.reservationId,
          label: entry.guestName,
          tooltip: `#${entry.reservationId} \u00B7 ${entry.guestName}\n`
            + `${this.formatDate(checkIn)} \u2192 ${this.formatDate(checkOut)} (${entry.nights} night(s))\n`
            + `Status: ${entry.status}\n`
            + 'Click to manage this reservation',
          status: entry.status,
          left: startOffset * this.dayWidth,
          width: Math.max(nights * this.dayWidth - 4, 18),
          clippedStart: checkIn < this.windowStart,
          clippedEnd: checkOut > windowEnd
        });
      }

      return { room: rt.room, bars, bookedNights };
    });
  }

  private formatDate(d: Date): string {
    return d.toLocaleDateString(undefined, { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  occupancyPercent(row: Row): number {
    if (!this.windowDays) return 0;
    return Math.round((row.bookedNights / this.windowDays) * 100);
  }

  selectReservation(bar: Bar): void {
    // A click that merely ended a drag must not open the dialog.
    if (this.dragMoved) return;
    this.reservationSelected.emit(bar.reservationId);
  }

  trackByRoom = (_: number, row: Row) => row.room.id;
  trackByBar = (_: number, bar: Bar) => bar.reservationId;
  trackByDay = (_: number, day: DayColumn) => day.date.getTime();
}
