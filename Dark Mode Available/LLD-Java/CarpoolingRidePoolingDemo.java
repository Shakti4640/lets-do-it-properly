/*
 * Carpooling / Ride Pooling - Low Level Design
 * --------------------------------------------------------------
 * Drivers offer rides along an ordered list of stops with a number of
 * available seats. Riders request a trip from an origin to a destination;
 * the matcher finds rides whose route covers the rider's segment in order
 * and still has free seats, then books a seat.
 *
 * Key classes / abstractions:
 *   - Ride          : driver, ordered route stops, seats.
 *   - RideRequest   : rider origin/destination + seats needed.
 *   - Booking       : confirmed seat allocation.
 *   - RidePoolService : matches requests to rides and books seats.
 *
 * Patterns used:
 *   - Strategy (matching as route-coverage check)
 *   - Facade   (RidePoolService coordinates rides + bookings)
 */

import java.util.*;
import java.util.stream.Collectors;

public class CarpoolingRidePoolingDemo {
    public static void main(String[] args) {
        RidePoolService service = new RidePoolService();

        service.offerRide("Ravi", Arrays.asList("Koramangala", "BTM", "Silk Board", "Electronic City"), 3);
        service.offerRide("Meena", Arrays.asList("Indiranagar", "Koramangala", "BTM", "Bannerghatta"), 2);
        service.offerRide("Sunil", Arrays.asList("Whitefield", "Marathahalli", "Silk Board"), 1);

        System.out.println("\n=== Rider wants Koramangala -> Electronic City (1 seat) ===");
        List<Ride> matches = service.findMatches(new RideRequest("Asha", "Koramangala", "Electronic City", 1));
        printMatches(matches);
        service.book(matches, new RideRequest("Asha", "Koramangala", "Electronic City", 1));

        System.out.println("\n=== Rider wants BTM -> Bannerghatta (2 seats) ===");
        RideRequest r2 = new RideRequest("Vikram", "BTM", "Bannerghatta", 2);
        List<Ride> m2 = service.findMatches(r2);
        printMatches(m2);
        service.book(m2, r2);

        System.out.println("\n=== Rider wants Silk Board -> Whitefield (wrong direction) ===");
        RideRequest r3 = new RideRequest("Neha", "Silk Board", "Whitefield", 1);
        List<Ride> m3 = service.findMatches(r3);
        printMatches(m3);

        System.out.println("\n=== Seat availability ===");
        service.printRides();
    }

    private static void printMatches(List<Ride> matches) {
        if (matches.isEmpty()) {
            System.out.println("  (no matching rides)");
            return;
        }
        for (Ride r : matches) System.out.println("  match -> " + r);
    }
}

class Ride {
    private final String id;
    private final String driver;
    private final List<String> stops;
    private int availableSeats;

    public Ride(String id, String driver, List<String> stops, int seats) {
        this.id = id;
        this.driver = driver;
        this.stops = new ArrayList<>(stops);
        this.availableSeats = seats;
    }

    public String getId() { return id; }
    public String getDriver() { return driver; }
    public int getAvailableSeats() { return availableSeats; }

    /** True if the route passes through 'from' then later 'to'. */
    public boolean covers(String from, String to) {
        int i = stops.indexOf(from);
        int j = stops.indexOf(to);
        return i >= 0 && j > i;
    }

    public boolean hasSeats(int n) { return availableSeats >= n; }

    public void reserve(int n) { availableSeats -= n; }

    @Override
    public String toString() {
        return id + " by " + driver + " " + stops + " seats=" + availableSeats;
    }
}

class RideRequest {
    private final String rider;
    private final String origin;
    private final String destination;
    private final int seats;

    public RideRequest(String rider, String origin, String destination, int seats) {
        this.rider = rider;
        this.origin = origin;
        this.destination = destination;
        this.seats = seats;
    }

    public String getRider() { return rider; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public int getSeats() { return seats; }
}

class Booking {
    private final String rideId;
    private final String rider;
    private final int seats;

    public Booking(String rideId, String rider, int seats) {
        this.rideId = rideId;
        this.rider = rider;
        this.seats = seats;
    }

    @Override
    public String toString() {
        return rider + " booked " + seats + " seat(s) on " + rideId;
    }
}

class RidePoolService {
    private final List<Ride> rides = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();
    private int rideCounter = 0;

    public Ride offerRide(String driver, List<String> stops, int seats) {
        Ride ride = new Ride("RIDE-" + (++rideCounter), driver, stops, seats);
        rides.add(ride);
        System.out.println("Offered " + ride);
        return ride;
    }

    public List<Ride> findMatches(RideRequest req) {
        return rides.stream()
                .filter(r -> r.covers(req.getOrigin(), req.getDestination()))
                .filter(r -> r.hasSeats(req.getSeats()))
                .collect(Collectors.toList());
    }

    public void book(List<Ride> matches, RideRequest req) {
        if (matches.isEmpty()) {
            System.out.println("  No ride to book for " + req.getRider());
            return;
        }
        Ride chosen = matches.get(0); // pick first match (could rank by detour)
        chosen.reserve(req.getSeats());
        Booking b = new Booking(chosen.getId(), req.getRider(), req.getSeats());
        bookings.add(b);
        System.out.println("  BOOKED: " + b + " (seats left " + chosen.getAvailableSeats() + ")");
    }

    public void printRides() {
        for (Ride r : rides) System.out.println("  " + r);
    }
}
