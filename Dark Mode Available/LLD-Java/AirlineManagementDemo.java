/*
 * Airline Management System - Low Level Design
 * --------------------------------------------
 * Flights with seats, search by route, booking with seat assignment.
 *
 * Key classes:
 *   - Airport / Flight / Seat (SeatClass)
 *   - Passenger / Booking
 *   - AirlineService (facade: search + book)
 *
 * Patterns used: Facade, Encapsulation, Enum-driven pricing.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AirlineManagementDemo {
    public static void main(String[] args) {
        AirlineService service = new AirlineService();
        Flight f1 = new Flight("AI101", "DEL", "BLR", "08:00");
        f1.addSeats(SeatClass.ECONOMY, 2, 5000);
        f1.addSeats(SeatClass.BUSINESS, 1, 12000);
        service.addFlight(f1);

        System.out.println("=== Airline Management Demo ===");
        System.out.println("DEL->BLR flights: " + service.search("DEL", "BLR"));

        Passenger p1 = new Passenger("P1", "Alice");
        Passenger p2 = new Passenger("P2", "Bob");
        Passenger p3 = new Passenger("P3", "Carol");

        Booking b1 = service.book(p1, "AI101", SeatClass.ECONOMY);
        Booking b2 = service.book(p2, "AI101", SeatClass.ECONOMY);
        Booking b3 = service.book(p3, "AI101", SeatClass.ECONOMY); // full
        Booking b4 = service.book(p3, "AI101", SeatClass.BUSINESS);
    }
}

enum SeatClass { ECONOMY, BUSINESS, FIRST }

class Airport {
    final String code; Airport(String code) { this.code = code; }
}

class Seat {
    final String number; final SeatClass seatClass; final double price; boolean booked = false;
    Seat(String number, SeatClass seatClass, double price) { this.number = number; this.seatClass = seatClass; this.price = price; }
}

class Flight {
    final String number; final String from; final String to; final String departure;
    private final List<Seat> seats = new ArrayList<Seat>();
    private int seatSeq = 0;
    Flight(String number, String from, String to, String departure) {
        this.number = number; this.from = from; this.to = to; this.departure = departure;
    }
    void addSeats(SeatClass cls, int count, double price) {
        for (int i = 0; i < count; i++) seats.add(new Seat(cls.name().charAt(0) + "" + (++seatSeq), cls, price));
    }
    Seat findAvailable(SeatClass cls) {
        for (Seat s : seats) if (!s.booked && s.seatClass == cls) return s;
        return null;
    }
    public String toString() { return number + " " + from + "->" + to + " @" + departure; }
}

class Passenger { final String id; final String name; Passenger(String id, String name) { this.id = id; this.name = name; } }

class Booking {
    final String id; final Passenger passenger; final Flight flight; final Seat seat;
    Booking(String id, Passenger passenger, Flight flight, Seat seat) {
        this.id = id; this.passenger = passenger; this.flight = flight; this.seat = seat;
    }
}

class AirlineService {
    private final Map<String, Flight> flights = new HashMap<String, Flight>();
    private int bookingSeq = 0;

    void addFlight(Flight f) { flights.put(f.number, f); }

    List<Flight> search(String from, String to) {
        List<Flight> result = new ArrayList<Flight>();
        for (Flight f : flights.values()) if (f.from.equals(from) && f.to.equals(to)) result.add(f);
        return result;
    }

    Booking book(Passenger p, String flightNumber, SeatClass cls) {
        Flight f = flights.get(flightNumber);
        if (f == null) { System.out.println("No flight: " + flightNumber); return null; }
        Seat seat = f.findAvailable(cls);
        if (seat == null) { System.out.println("No " + cls + " seats on " + flightNumber + " for " + p.name); return null; }
        seat.booked = true;
        Booking b = new Booking("BK" + (++bookingSeq), p, f, seat);
        System.out.println(p.name + " booked seat " + seat.number + " (" + cls + ", Rs " + seat.price + ") on " + flightNumber);
        return b;
    }
}
