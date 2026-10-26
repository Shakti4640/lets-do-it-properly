/*
 * Concert Ticket Booking - Low Level Design
 * -----------------------------------------
 * Models events with a seat map organized by zones. Seats can be held (locked)
 * with a deterministic expiry, then confirmed into a booking after payment.
 * Double-booking is prevented and expired holds are released automatically.
 *
 * Key classes / abstractions:
 *   - Event, Zone, Seat (+ SeatStatus enum)
 *   - SeatHold (lock with expiry), Booking
 *   - BookingService (hold/confirm/release using injected Clock)
 *
 * Patterns used: State (SeatStatus), Facade (BookingService), deterministic Clock injection.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConcertTicketBookingDemo {
    public static void main(String[] args) {
        System.out.println("=== Concert Ticket Booking Demo ===");
        Clock clock = new Clock(0L);
        BookingService service = new BookingService(clock);

        Event event = service.createEvent("E1", "Rock Night");
        event.addZone(new Zone("VIP", 199.0, new String[]{"V1", "V2"}));
        event.addZone(new Zone("GA", 49.0, new String[]{"G1", "G2", "G3"}));
        System.out.println("Created event '" + event.name + "' with seats: " + event.allSeatIds());

        // Alice holds two seats
        System.out.println("\n-- Hold seats --");
        SeatHold hold = service.hold("E1", "alice", new String[]{"V1", "G1"});
        System.out.println("alice holds " + hold.seatIds + " (expires@" + hold.expiry + ")");

        // Bob tries to hold one of the same seats -> blocked
        try {
            service.hold("E1", "bob", new String[]{"V1"});
        } catch (IllegalStateException e) {
            System.out.println("bob hold failed: " + e.getMessage());
        }

        // Alice confirms with payment
        System.out.println("\n-- Confirm booking --");
        Booking booking = service.confirm("E1", hold.holdId, "VISA-OK");
        System.out.println("Booking " + booking.bookingId + " seats=" + booking.seatIds
                + " total=$" + booking.total + " status=" + booking.status);

        // Bob now holds a seat then lets it expire
        System.out.println("\n-- Hold expiry releases seats --");
        SeatHold bobHold = service.hold("E1", "bob", new String[]{"G2"});
        System.out.println("bob holds " + bobHold.seatIds + " (expires@" + bobHold.expiry + ")");
        clock.advance(120L);
        service.releaseExpired("E1");
        System.out.println("after t=" + clock.now() + " G2 status=" + event.seat("G2").status);

        // Carol can now grab G2
        SeatHold carolHold = service.hold("E1", "carol", new String[]{"G2"});
        Booking carolBooking = service.confirm("E1", carolHold.holdId, "VISA-OK");
        System.out.println("carol booked " + carolBooking.seatIds + " status=" + carolBooking.status);

        System.out.println("\n-- Final seat map --");
        for (Seat s : event.seats()) {
            System.out.println("  " + s.id + " [" + s.zoneName + "] " + s.status
                    + (s.ownerId != null ? " -> " + s.ownerId : ""));
        }
    }
}

class Clock {
    private long current;
    Clock(long start) { this.current = start; }
    long now() { return current; }
    void advance(long delta) { current += delta; }
}

enum SeatStatus { AVAILABLE, HELD, BOOKED }

class Seat {
    final String id;
    final String zoneName;
    final double price;
    SeatStatus status = SeatStatus.AVAILABLE;
    String ownerId;
    Seat(String id, String zoneName, double price) { this.id = id; this.zoneName = zoneName; this.price = price; }
}

class Zone {
    final String name;
    final double price;
    final String[] seatIds;
    Zone(String name, double price, String[] seatIds) { this.name = name; this.price = price; this.seatIds = seatIds; }
}

class Event {
    final String id;
    final String name;
    private final LinkedHashMap<String, Seat> seats = new LinkedHashMap<String, Seat>();
    Event(String id, String name) { this.id = id; this.name = name; }

    void addZone(Zone zone) {
        for (String sid : zone.seatIds) seats.put(sid, new Seat(sid, zone.name, zone.price));
    }
    Seat seat(String id) { return seats.get(id); }
    List<Seat> seats() { return new ArrayList<Seat>(seats.values()); }
    List<String> allSeatIds() { return new ArrayList<String>(seats.keySet()); }
}

class SeatHold {
    final String holdId;
    final String userId;
    final List<String> seatIds;
    final long expiry;
    boolean active = true;
    SeatHold(String holdId, String userId, List<String> seatIds, long expiry) {
        this.holdId = holdId; this.userId = userId; this.seatIds = seatIds; this.expiry = expiry;
    }
}

class Booking {
    final String bookingId;
    final String userId;
    final List<String> seatIds;
    final double total;
    final String status;
    Booking(String bookingId, String userId, List<String> seatIds, double total, String status) {
        this.bookingId = bookingId; this.userId = userId; this.seatIds = seatIds;
        this.total = total; this.status = status;
    }
}

class BookingService {
    private static final long HOLD_TTL = 60L;
    private final Clock clock;
    private int seq = 0;
    private final Map<String, Event> events = new HashMap<String, Event>();
    private final Map<String, SeatHold> holds = new HashMap<String, SeatHold>();

    BookingService(Clock clock) { this.clock = clock; }

    Event createEvent(String id, String name) {
        Event e = new Event(id, name);
        events.put(id, e);
        return e;
    }

    SeatHold hold(String eventId, String userId, String[] seatIds) {
        Event event = require(eventId);
        List<String> ids = new ArrayList<String>();
        for (String sid : seatIds) {
            Seat seat = event.seat(sid);
            if (seat == null) throw new IllegalArgumentException("No such seat: " + sid);
            if (seat.status != SeatStatus.AVAILABLE) {
                throw new IllegalStateException("seat " + sid + " is " + seat.status);
            }
            ids.add(sid);
        }
        for (String sid : ids) {
            Seat seat = event.seat(sid);
            seat.status = SeatStatus.HELD;
            seat.ownerId = userId;
        }
        SeatHold h = new SeatHold("H" + (++seq), userId, ids, clock.now() + HOLD_TTL);
        holds.put(h.holdId, h);
        return h;
    }

    Booking confirm(String eventId, String holdId, String paymentToken) {
        Event event = require(eventId);
        SeatHold h = holds.get(holdId);
        if (h == null || !h.active) throw new IllegalStateException("hold not active");
        if (clock.now() > h.expiry) throw new IllegalStateException("hold expired");
        if (!"VISA-OK".equals(paymentToken)) throw new IllegalStateException("payment declined");

        double total = 0;
        for (String sid : h.seatIds) {
            Seat seat = event.seat(sid);
            seat.status = SeatStatus.BOOKED;
            total += seat.price;
        }
        h.active = false;
        return new Booking("B" + (++seq), h.userId, h.seatIds, total, "CONFIRMED");
    }

    void releaseExpired(String eventId) {
        Event event = require(eventId);
        for (SeatHold h : holds.values()) {
            if (h.active && clock.now() > h.expiry) {
                for (String sid : h.seatIds) {
                    Seat seat = event.seat(sid);
                    if (seat != null && seat.status == SeatStatus.HELD) {
                        seat.status = SeatStatus.AVAILABLE;
                        seat.ownerId = null;
                    }
                }
                h.active = false;
            }
        }
    }

    private Event require(String eventId) {
        Event e = events.get(eventId);
        if (e == null) throw new IllegalArgumentException("No such event: " + eventId);
        return e;
    }
}
