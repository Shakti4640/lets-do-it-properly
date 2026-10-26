/*
 * Parking Lot - Low Level Design
 * --------------------------------
 * Models a multi-level parking lot supporting Bike, Car and Truck vehicles.
 *
 * Key classes:
 *   - VehicleType / SpotType : enums describing vehicle and spot sizes.
 *   - Vehicle (Bike/Car/Truck) : vehicle hierarchy.
 *   - ParkingSpot              : an individual spot of a given type.
 *   - ParkingLevel             : a floor holding many spots.
 *   - ParkingLot (singleton)   : aggregates levels, issues tickets.
 *   - SpotAllocationStrategy   : Strategy pattern for choosing a spot
 *                                (NearestFirstStrategy implemented).
 *   - Ticket / PaymentProcessor: ticket lifecycle + fee calculation.
 *
 * Patterns used: Singleton (ParkingLot), Strategy (spot allocation),
 *                Factory-ish vehicle creation, Enums, Encapsulation.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ParkingLotDemo {
    public static void main(String[] args) {
        ParkingLot lot = ParkingLot.getInstance();
        lot.addLevel(new ParkingLevel(1, 2, 3, 1));
        lot.addLevel(new ParkingLevel(2, 1, 2, 1));
        lot.setAllocationStrategy(new NearestFirstStrategy());

        System.out.println("=== Parking Lot Demo ===");
        Vehicle bike = new Bike("KA-01-1111");
        Vehicle car1 = new Car("KA-02-2222");
        Vehicle car2 = new Car("KA-03-3333");
        Vehicle truck = new Truck("KA-04-4444");

        Ticket t1 = lot.parkVehicle(bike);
        Ticket t2 = lot.parkVehicle(car1);
        Ticket t3 = lot.parkVehicle(car2);
        Ticket t4 = lot.parkVehicle(truck);

        lot.printAvailability();

        // Simulate exit after some time
        if (t2 != null) {
            double fee = lot.unparkVehicle(t2, 3); // parked 3 hours
            System.out.println("Car " + car1.getPlate() + " paid: $" + fee);
        }

        lot.printAvailability();

        // Park another car now that a slot is free
        Vehicle car3 = new Car("KA-05-5555");
        Ticket t5 = lot.parkVehicle(car3);
        System.out.println("New car ticket: " + (t5 != null ? t5.getId() : "REJECTED"));
        lot.printAvailability();
    }
}

enum VehicleType { BIKE, CAR, TRUCK }

enum SpotType {
    SMALL, MEDIUM, LARGE;

    static SpotType requiredFor(VehicleType vt) {
        switch (vt) {
            case BIKE: return SMALL;
            case CAR:  return MEDIUM;
            default:   return LARGE;
        }
    }
}

abstract class Vehicle {
    private final String plate;
    private final VehicleType type;

    Vehicle(String plate, VehicleType type) {
        this.plate = plate;
        this.type = type;
    }
    String getPlate() { return plate; }
    VehicleType getType() { return type; }
}

class Bike extends Vehicle { Bike(String p) { super(p, VehicleType.BIKE); } }
class Car extends Vehicle { Car(String p) { super(p, VehicleType.CAR); } }
class Truck extends Vehicle { Truck(String p) { super(p, VehicleType.TRUCK); } }

class ParkingSpot {
    private final String id;
    private final SpotType type;
    private Vehicle current;

    ParkingSpot(String id, SpotType type) {
        this.id = id;
        this.type = type;
    }
    String getId() { return id; }
    SpotType getType() { return type; }
    boolean isFree() { return current == null; }

    boolean canFit(Vehicle v) {
        return isFree() && SpotType.requiredFor(v.getType()) == type;
    }
    void assign(Vehicle v) { this.current = v; }
    void release() { this.current = null; }
}

class ParkingLevel {
    private final int floor;
    private final List<ParkingSpot> spots = new ArrayList<ParkingSpot>();

    ParkingLevel(int floor, int small, int medium, int large) {
        this.floor = floor;
        int idx = 0;
        for (int i = 0; i < small; i++)  spots.add(new ParkingSpot("F" + floor + "-S" + (idx++), SpotType.SMALL));
        for (int i = 0; i < medium; i++) spots.add(new ParkingSpot("F" + floor + "-M" + (idx++), SpotType.MEDIUM));
        for (int i = 0; i < large; i++)  spots.add(new ParkingSpot("F" + floor + "-L" + (idx++), SpotType.LARGE));
    }
    int getFloor() { return floor; }
    List<ParkingSpot> getSpots() { return spots; }

    Map<SpotType, Integer> availability() {
        Map<SpotType, Integer> map = new HashMap<SpotType, Integer>();
        for (SpotType st : SpotType.values()) map.put(st, 0);
        for (ParkingSpot s : spots) if (s.isFree()) map.put(s.getType(), map.get(s.getType()) + 1);
        return map;
    }
}

interface SpotAllocationStrategy {
    ParkingSpot findSpot(List<ParkingLevel> levels, Vehicle v);
}

class NearestFirstStrategy implements SpotAllocationStrategy {
    public ParkingSpot findSpot(List<ParkingLevel> levels, Vehicle v) {
        for (ParkingLevel level : levels) {
            for (ParkingSpot spot : level.getSpots()) {
                if (spot.canFit(v)) return spot;
            }
        }
        return null;
    }
}

class Ticket {
    private final String id;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final long issuedAt;

    Ticket(String id, Vehicle vehicle, ParkingSpot spot) {
        this.id = id;
        this.vehicle = vehicle;
        this.spot = spot;
        this.issuedAt = System.currentTimeMillis();
    }
    String getId() { return id; }
    Vehicle getVehicle() { return vehicle; }
    ParkingSpot getSpot() { return spot; }
    long getIssuedAt() { return issuedAt; }
}

class PaymentProcessor {
    private static final Map<SpotType, Double> HOURLY = new HashMap<SpotType, Double>();
    static {
        HOURLY.put(SpotType.SMALL, 1.0);
        HOURLY.put(SpotType.MEDIUM, 2.0);
        HOURLY.put(SpotType.LARGE, 4.0);
    }
    static double charge(Ticket ticket, int hours) {
        double rate = HOURLY.get(ticket.getSpot().getType());
        return rate * Math.max(1, hours);
    }
}

class ParkingLot {
    private static ParkingLot instance;
    private final List<ParkingLevel> levels = new ArrayList<ParkingLevel>();
    private SpotAllocationStrategy strategy = new NearestFirstStrategy();
    private int ticketCounter = 0;

    private ParkingLot() {}

    static synchronized ParkingLot getInstance() {
        if (instance == null) instance = new ParkingLot();
        return instance;
    }

    void addLevel(ParkingLevel level) { levels.add(level); }
    void setAllocationStrategy(SpotAllocationStrategy s) { this.strategy = s; }

    Ticket parkVehicle(Vehicle v) {
        ParkingSpot spot = strategy.findSpot(levels, v);
        if (spot == null) {
            System.out.println("No spot for " + v.getType() + " " + v.getPlate());
            return null;
        }
        spot.assign(v);
        Ticket ticket = new Ticket("T" + (++ticketCounter), v, spot);
        System.out.println("Parked " + v.getType() + " " + v.getPlate() + " at " + spot.getId() + " (ticket " + ticket.getId() + ")");
        return ticket;
    }

    double unparkVehicle(Ticket ticket, int hours) {
        double fee = PaymentProcessor.charge(ticket, hours);
        ticket.getSpot().release();
        System.out.println("Released " + ticket.getSpot().getId() + " for ticket " + ticket.getId());
        return fee;
    }

    void printAvailability() {
        System.out.println("--- Availability ---");
        for (ParkingLevel level : levels) {
            System.out.println("Floor " + level.getFloor() + ": " + level.availability());
        }
    }
}
