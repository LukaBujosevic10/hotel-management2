/** A room is never permanently "occupied" -- it is only booked for concrete periods. */
export type RoomStatus = 'AVAILABLE' | 'MAINTENANCE';

export type ReservationStatus =
  'PENDING' | 'CONFIRMED' | 'CHECKED_IN' | 'CHECKED_OUT' | 'CANCELLED';

export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';

export interface Guest {
  id?: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  documentId: string;
  loyaltyPoints?: number;
}

export interface Room {
  id?: number;
  roomNumber: string;
  type: string;
  floor: number;
  pricePerNight: number;
  capacity: number;
  /** AVAILABLE = bookable, MAINTENANCE = out of order. Says nothing about dates. */
  status?: RoomStatus;
  description?: string;
}

export interface Reservation {
  id?: number;
  guestId: number;
  roomId: number;
  checkInDate: string;
  checkOutDate: string;
  numberOfGuests: number;
  totalPrice?: number;
  status?: ReservationStatus;
  createdAt?: string;
}

export interface ReservationDetails {
  reservation: Reservation;
  guest: Guest | null;
  room: Room | null;
  nights: number;
}

export interface Payment {
  id?: number;
  reservationId: number;
  amount: number;
  taxAmount: number;
  status: PaymentStatus;
  method?: string;
  transactionRef?: string;
  createdAt?: string;
  paidAt?: string;
}

/** One occupancy bar on the rooms timeline. */
export interface TimelineEntry {
  reservationId: number;
  roomId: number;
  roomNumber: string;
  guestId: number;
  guestName: string;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  status: ReservationStatus;
}

export interface RoomTimeline {
  room: Room;
  entries: TimelineEntry[];
}

export interface TimelineResponse {
  from: string;
  to: string;
  rooms: RoomTimeline[];
}
