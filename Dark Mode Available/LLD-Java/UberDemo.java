/*
 * Uber (Ride Hailing) - Low Level Design
 * --------------------------------------
 * Matches riders to nearby available drivers, manages trip lifecycle and
 * computes fare. Driver matching uses a pluggable strategy (nearest).
 *
 * Key classes:
 *   - Location / Rider / Driver
 *   - DriverMatchingStrategy (+ NearestDriverStrategy)
 *   - Trip (lifecycle: REQUESTED -> ONGOING -> COMPLETED)
 *   - RideService (facade)
 *
 * Patterns used: Strategy (matching), State-ish trip status, Facade.
 */

import java.util.ArrayList;
import java.util.List;

public class UberDemo {
    public static void main(String[] args) {
        RideService service = new RideService();
        service.registerDriver(new Driver("D1", "Sam", new Location(0, 0)));
        service.registerDriver(new Driver("D2", "Pat", new Location(5, 5)));
        service.registerDriver(new Driver("D3", "Lee", new Location(1, 1)));

        Rider rider = new Rider("R1", "Alice", new Location(1, 0));

        System.out.println("=== Uber Demo ===");
        Trip trip = service.requestRide(rider, new Location(10, 10));
        if (trip != null) {
            service.startTrip(trip);
            double fare = service.completeTrip(trip);
            System.out.println("Fare: $" + String.format("%.2f", fare));
        }
        // Another request - nearest among remaining
        Rider rider2 = new Rider("R2", "Bob", new Location(4, 4));
        Trip t2 = service.requestRide(rider2, new Location(0, 0));
    }
}

class Location {
    final double x, y;
    Location(double x, double y) { this.x = x; this.y = y; }
    double distanceTo(Location o) { return Math.sqrt(Math.pow(x - o.x, 2) + Math.pow(y - o.y, 2)); }
    public String toString() { return "(" + x + "," + y + ")"; }
}

class Rider {
    final String id; final String name; Location location;
    Rider(String id, String name, Location location) { this.id = id; this.name = name; this.location = location; }
}

class Driver {
    final String id; final String name; Location location; boolean available = true;
    Driver(String id, String name, Location location) { this.id = id; this.name = name; this.location = location; }
}

enum TripStatus { REQUESTED, ONGOING, COMPLETED, CANCELLED }

class Trip {
    final String id; final Rider rider; final Driver driver;
    final Location source; final Location destination;
    TripStatus status = TripStatus.REQUESTED;
    Trip(String id, Rider rider, Driver driver, Location source, Location destination) {
        this.id = id; this.rider = rider; this.driver = driver; this.source = source; this.destination = destination;
    }
}

interface DriverMatchingStrategy {
    Driver match(List<Driver> drivers, Location pickup);
}

class NearestDriverStrategy implements DriverMatchingStrategy {
    public Driver match(List<Driver> drivers, Location pickup) {
        Driver best = null; double bestDist = Double.MAX_VALUE;
        for (Driver d : drivers) {
            if (!d.available) continue;
            double dist = d.location.distanceTo(pickup);
            if (dist < bestDist) { bestDist = dist; best = d; }
        }
        return best;
    }
}

class RideService {
    private final List<Driver> drivers = new ArrayList<Driver>();
    private DriverMatchingStrategy strategy = new NearestDriverStrategy();
    private final double baseFare = 2.0;
    private final double perUnit = 1.5;
    private int tripSeq = 0;

    void registerDriver(Driver d) { drivers.add(d); }
    void setStrategy(DriverMatchingStrategy s) { this.strategy = s; }

    Trip requestRide(Rider rider, Location destination) {
        Driver driver = strategy.match(drivers, rider.location);
        if (driver == null) { System.out.println("No drivers available for " + rider.name); return null; }
        driver.available = false;
        Trip trip = new Trip("TR" + (++tripSeq), rider, driver, rider.location, destination);
        System.out.println(rider.name + " matched with " + driver.name + " (dist=" 
                + String.format("%.2f", driver.location.distanceTo(rider.location)) + ")");
        return trip;
    }

    void startTrip(Trip trip) {
        trip.status = TripStatus.ONGOING;
        System.out.println("Trip " + trip.id + " started.");
    }

    double completeTrip(Trip trip) {
        trip.status = TripStatus.COMPLETED;
        trip.driver.available = true;
        trip.driver.location = trip.destination;
        double distance = trip.source.distanceTo(trip.destination);
        System.out.println("Trip " + trip.id + " completed. Distance=" + String.format("%.2f", distance));
        return baseFare + distance * perUnit;
    }
}
