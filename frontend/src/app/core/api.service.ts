import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Guest, Room, Reservation, ReservationDetails, Payment, TimelineResponse } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // ------------------------------------------------------------------ Guests
  getGuests(): Observable<Guest[]> {
    return this.http.get<Guest[]>(`${this.base}/api/guests`);
  }
  createGuest(g: Guest): Observable<Guest> {
    return this.http.post<Guest>(`${this.base}/api/guests`, g);
  }
  updateGuest(id: number, g: Guest): Observable<Guest> {
    return this.http.put<Guest>(`${this.base}/api/guests/${id}`, g);
  }
  deleteGuest(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/guests/${id}`);
  }

  // ------------------------------------------------------------------- Rooms
  getRooms(): Observable<Room[]> {
    return this.http.get<Room[]>(`${this.base}/api/rooms`);
  }
  createRoom(r: Room): Observable<Room> {
    return this.http.post<Room>(`${this.base}/api/rooms`, r);
  }
  updateRoom(id: number, r: Room): Observable<Room> {
    return this.http.put<Room>(`${this.base}/api/rooms/${id}`, r);
  }
  deleteRoom(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/rooms/${id}`);
  }
  /** Only AVAILABLE <-> MAINTENANCE. Occupancy is decided by reservations. */
  setRoomStatus(id: number, status: 'AVAILABLE' | 'MAINTENANCE'): Observable<Room> {
    return this.http.patch<Room>(`${this.base}/api/rooms/${id}/status`, null,
      { params: new HttpParams().set('status', status) });
  }

  // ------------------------------------------------------------ Reservations
  getReservations(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(`${this.base}/api/reservations`);
  }
  createReservation(r: Reservation): Observable<Reservation> {
    return this.http.post<Reservation>(`${this.base}/api/reservations`, r);
  }
  updateReservation(id: number, r: Reservation): Observable<Reservation> {
    return this.http.put<Reservation>(`${this.base}/api/reservations/${id}`, r);
  }
  getReservationDetails(id: number): Observable<ReservationDetails> {
    return this.http.get<ReservationDetails>(`${this.base}/api/reservations/${id}/details`);
  }
  setReservationStatus(id: number, status: string): Observable<Reservation> {
    return this.http.patch<Reservation>(`${this.base}/api/reservations/${id}/status`, null,
      { params: new HttpParams().set('status', status) });
  }
  deleteReservation(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/reservations/${id}`);
  }

  /**
   * The only correct way to ask "is this room free?" -- always for a period.
   * Dates must be yyyy-MM-dd (see date.util#toApiDate).
   */
  getAvailableRooms(checkIn: string, checkOut: string,
                    numberOfGuests?: number,
                    excludeReservationId?: number): Observable<Room[]> {
    let params = new HttpParams().set('checkIn', checkIn).set('checkOut', checkOut);
    if (numberOfGuests && numberOfGuests > 0) {
      params = params.set('numberOfGuests', numberOfGuests);
    }
    // When editing, a reservation must not count as blocking its own room.
    if (excludeReservationId) {
      params = params.set('excludeReservationId', excludeReservationId);
    }
    return this.http.get<Room[]>(`${this.base}/api/reservations/availability`, { params });
  }

  /** Occupancy of every room over a window -- feeds the Gantt chart. */
  getTimeline(from: string, to: string): Observable<TimelineResponse> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<TimelineResponse>(`${this.base}/api/reservations/timeline`, { params });
  }

  // ---------------------------------------------------------------- Payments
  getPayments(): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.base}/api/payments`);
  }
  /** 404 when the RabbitMQ consumer has not created the payment yet. */
  getPaymentByReservation(reservationId: number): Observable<Payment> {
    return this.http.get<Payment>(`${this.base}/api/payments/reservation/${reservationId}`);
  }
  pay(id: number, method: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/api/payments/${id}/pay`, { method });
  }
  refund(id: number): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/api/payments/${id}/refund`, {});
  }
}
