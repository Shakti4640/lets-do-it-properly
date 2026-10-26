/*
 * Elevator System - Low Level Design
 * ----------------------------------
 * Multiple elevators served by a controller that dispatches requests to
 * the nearest suitable car. Each car processes its requests by direction.
 *
 * Key classes:
 *   - Direction               : UP, DOWN, IDLE.
 *   - Request                 : a floor request (external/internal).
 *   - Elevator                : a car with current floor, direction, stops.
 *   - ElevatorController       : dispatches requests (nearest-car strategy).
 *
 * Patterns used: Strategy (dispatch), State-ish direction handling.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class ElevatorSystemDemo {
    public static void main(String[] args) {
        ElevatorController controller = new ElevatorController(2, 10);
        System.out.println("=== Elevator Demo ===");
        controller.requestElevator(3, Direction.UP);
        controller.requestElevator(7, Direction.DOWN);
        controller.requestElevator(1, Direction.UP);

        // Run a few simulation steps
        for (int step = 0; step < 8; step++) {
            System.out.println("-- step " + step + " --");
            controller.step();
        }
    }
}

enum Direction { UP, DOWN, IDLE }

class Request {
    final int floor;
    final Direction direction;
    Request(int floor, Direction direction) { this.floor = floor; this.direction = direction; }
}

class Elevator {
    private final int id;
    private final int maxFloor;
    private int currentFloor = 0;
    private Direction direction = Direction.IDLE;
    private final TreeSet<Integer> upStops = new TreeSet<Integer>();
    private final TreeSet<Integer> downStops = new TreeSet<Integer>();

    Elevator(int id, int maxFloor) { this.id = id; this.maxFloor = maxFloor; }
    int getId() { return id; }
    int getCurrentFloor() { return currentFloor; }
    Direction getDirection() { return direction; }

    void addStop(int floor) {
        if (floor > currentFloor) upStops.add(floor);
        else if (floor < currentFloor) downStops.add(floor);
        if (direction == Direction.IDLE) direction = floor > currentFloor ? Direction.UP : Direction.DOWN;
    }

    int distanceTo(int floor) { return Math.abs(currentFloor - floor); }

    void step() {
        if (direction == Direction.UP) {
            if (!upStops.isEmpty()) {
                currentFloor += currentFloor < upStops.first() ? 1 : 0;
                if (upStops.contains(currentFloor)) { upStops.remove(currentFloor); System.out.println("E" + id + " stop at " + currentFloor); }
            }
            if (upStops.isEmpty()) direction = downStops.isEmpty() ? Direction.IDLE : Direction.DOWN;
        } else if (direction == Direction.DOWN) {
            if (!downStops.isEmpty()) {
                currentFloor -= currentFloor > downStops.last() ? 1 : 0;
                if (downStops.contains(currentFloor)) { downStops.remove(currentFloor); System.out.println("E" + id + " stop at " + currentFloor); }
            }
            if (downStops.isEmpty()) direction = upStops.isEmpty() ? Direction.IDLE : Direction.UP;
        }
        System.out.println("E" + id + " floor=" + currentFloor + " dir=" + direction);
    }
}

class ElevatorController {
    private final List<Elevator> elevators = new ArrayList<Elevator>();
    ElevatorController(int count, int maxFloor) {
        for (int i = 1; i <= count; i++) elevators.add(new Elevator(i, maxFloor));
    }

    void requestElevator(int floor, Direction dir) {
        Elevator best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            int d = e.distanceTo(floor);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        if (best != null) {
            best.addStop(floor);
            System.out.println("Request floor " + floor + " " + dir + " -> Elevator " + best.getId());
        }
    }

    void step() { for (Elevator e : elevators) e.step(); }
}
