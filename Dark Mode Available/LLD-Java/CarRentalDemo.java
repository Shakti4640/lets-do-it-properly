/*
 * Car Rental System - Low Level Design
 * ------------------------------------
 * Search cars by type, reserve for a date range, pick up and return with
 * fee calculation.
 *
 * Key classes:
 *   - Car (CarType) / Store
 *   - DateRange / Reservation
 *   - CarRentalService (facade)
 *
 * Patterns used: Facade, interval availability, Encapsulation.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CarRentalDemo {
    public static void main(String[] args) {
        CarRentalService service = new CarRentalService();
        service.addCar(new Car("KA01", CarType.SEDAN, 2000));
        service.addCar(new Car("KA02", CarType.SEDAN, 2000));
        service.addCar(new Car("KA03", CarType.SUV, 3500));

        System.out.println("=== Car Rental Demo ===");
        System.out.println("Available SEDANs: " + service.search(CarType.SEDAN, new DateRange(1, 4)).size());

        Reservation r1 = service.reserve("Alice", CarType.SEDAN, new DateRange(1, 4)); // 3 days
        Reservation r2 = service.reserve("Bob", CarType.SEDAN, new DateRange(2, 3));   // 2nd sedan
        Reservation r3 = service.reserve("Carol", CarType.SEDAN, new DateRange(3, 5)); // none free

        if (r1 != null) {
            service.pickup(r1);
            double fee = service.returnCar(r1);
            System.out.println("Alice paid: Rs " + fee);
        }
    }
}

enum CarType { HATCHBACK, SEDAN, SUV }

class DateRange {
    final int start, end;
    DateRange(int start, int end) { this.start = start; this.end = end; }
    boolean overlaps(DateRange o) { return start < o.end && o.start < end; }
    int days() { return end - start; }
}

class Car {
    final String plate; final CarType type; final double dailyRate;
    final List<DateRange> booked = new ArrayList<DateRange>();
    boolean outForRental = false;
    Car(String plate, CarType type, double dailyRate) { this.plate = plate; this.type = type; this.dailyRate = dailyRate; }
    boolean isAvailable(DateRange range) {
        for (DateRange b : booked) if (b.overlaps(range)) return false;
        return true;
    }
}

class Reservation {
    final String id; final String customer; final Car car; final DateRange range;
    Reservation(String id, String customer, Car car, DateRange range) {
        this.id = id; this.customer = customer; this.car = car; this.range = range;
    }
}

class CarRentalService {
    private final List<Car> cars = new ArrayList<Car>();
    private final Map<String, Reservation> reservations = new HashMap<String, Reservation>();
    private int seq = 0;

    void addCar(Car c) { cars.add(c); }

    List<Car> search(CarType type, DateRange range) {
        List<Car> result = new ArrayList<Car>();
        for (Car c : cars) if (c.type == type && c.isAvailable(range)) result.add(c);
        return result;
    }

    Reservation reserve(String customer, CarType type, DateRange range) {
        for (Car c : cars) {
            if (c.type == type && c.isAvailable(range)) {
                c.booked.add(range);
                Reservation r = new Reservation("RSV" + (++seq), customer, c, range);
                reservations.put(r.id, r);
                System.out.println(customer + " reserved " + type + " " + c.plate + " for " + range.days() + " days");
                return r;
            }
        }
        System.out.println("No " + type + " available for " + customer);
        return null;
    }

    void pickup(Reservation r) { r.car.outForRental = true; System.out.println(r.customer + " picked up " + r.car.plate); }

    double returnCar(Reservation r) {
        r.car.outForRental = false;
        System.out.println(r.customer + " returned " + r.car.plate);
        return r.range.days() * r.car.dailyRate;
    }
}
