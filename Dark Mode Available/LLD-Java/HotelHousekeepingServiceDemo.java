/*
 * Hotel Housekeeping Service - Low Level Design
 * --------------------------------------------------------------
 * Manages cleaning of hotel rooms. Each room has a cleaning status
 * (DIRTY -> CLEANING -> CLEAN -> INSPECTED). Dirty rooms generate tasks
 * placed on a queue; housekeepers are assigned tasks, mark progress, and a
 * supervisor inspects finished rooms.
 *
 * Key classes / abstractions:
 *   - CleaningStatus   : room cleanliness state.
 *   - Room             : a room with current status.
 *   - HousekeepingTask : a unit of work tied to a room + assignee.
 *   - Housekeeper      : staff who can be assigned tasks.
 *   - HousekeepingService : task queue, assignment, progress, inspection.
 *
 * Patterns used:
 *   - State (CleaningStatus transitions)
 *   - Command/Queue (HousekeepingTask on a work queue)
 */

import java.util.*;

public class HotelHousekeepingServiceDemo {
    public static void main(String[] args) {
        HousekeepingService service = new HousekeepingService();
        service.addRoom(new Room("101"));
        service.addRoom(new Room("102"));
        service.addRoom(new Room("103"));

        Housekeeper anita = new Housekeeper("HK-1", "Anita");
        Housekeeper raj = new Housekeeper("HK-2", "Raj");
        service.addHousekeeper(anita);
        service.addHousekeeper(raj);

        System.out.println("=== Guests check out: rooms become DIRTY (tasks created) ===");
        service.markDirty("101");
        service.markDirty("102");
        service.markDirty("103");

        System.out.println("\n=== Assign tasks from queue ===");
        service.assignNext(anita); // 101
        service.assignNext(raj);   // 102

        System.out.println("\n=== Housekeepers start + finish cleaning ===");
        service.startCleaning("101");
        service.finishCleaning("101"); // CLEAN
        service.startCleaning("102");
        service.finishCleaning("102"); // CLEAN

        System.out.println("\n=== Supervisor inspects ===");
        service.inspect("101"); // INSPECTED
        service.inspect("102"); // INSPECTED
        service.inspect("103"); // not clean yet -> rejected

        System.out.println("\n=== Assign remaining task ===");
        service.assignNext(anita); // 103
        service.startCleaning("103");
        service.finishCleaning("103");
        service.inspect("103");

        System.out.println("\n=== Final room board ===");
        service.printBoard();
    }
}

enum CleaningStatus { DIRTY, CLEANING, CLEAN, INSPECTED }

class Room {
    private final String number;
    private CleaningStatus status = CleaningStatus.INSPECTED; // ready for guests initially

    public Room(String number) { this.number = number; }

    public String getNumber() { return number; }
    public CleaningStatus getStatus() { return status; }
    public void setStatus(CleaningStatus status) { this.status = status; }
}

class Housekeeper {
    private final String id;
    private final String name;

    public Housekeeper(String id, String name) { this.id = id; this.name = name; }
    public String getId() { return id; }
    public String getName() { return name; }
}

class HousekeepingTask {
    private final String roomNumber;
    private String assigneeName;

    public HousekeepingTask(String roomNumber) { this.roomNumber = roomNumber; }

    public String getRoomNumber() { return roomNumber; }
    public String getAssigneeName() { return assigneeName; }
    public void assignTo(String name) { this.assigneeName = name; }
}

class HousekeepingService {
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, Housekeeper> staff = new LinkedHashMap<>();
    private final Queue<HousekeepingTask> queue = new LinkedList<>();
    private final Map<String, HousekeepingTask> activeByRoom = new HashMap<>();

    public void addRoom(Room room) { rooms.put(room.getNumber(), room); }
    public void addHousekeeper(Housekeeper hk) { staff.put(hk.getId(), hk); }

    public void markDirty(String roomNumber) {
        Room room = rooms.get(roomNumber);
        if (room == null) return;
        room.setStatus(CleaningStatus.DIRTY);
        HousekeepingTask task = new HousekeepingTask(roomNumber);
        queue.add(task);
        activeByRoom.put(roomNumber, task);
        System.out.println("  Room " + roomNumber + " -> DIRTY (task queued)");
    }

    public void assignNext(Housekeeper hk) {
        HousekeepingTask task = queue.poll();
        if (task == null) {
            System.out.println("  No tasks in queue for " + hk.getName());
            return;
        }
        task.assignTo(hk.getName());
        System.out.println("  Task for room " + task.getRoomNumber() + " assigned to " + hk.getName());
    }

    public void startCleaning(String roomNumber) {
        Room room = rooms.get(roomNumber);
        if (room == null || room.getStatus() != CleaningStatus.DIRTY) {
            System.out.println("  Cannot start cleaning room " + roomNumber + " (status "
                    + (room == null ? "N/A" : room.getStatus()) + ")");
            return;
        }
        room.setStatus(CleaningStatus.CLEANING);
        System.out.println("  Room " + roomNumber + " -> CLEANING");
    }

    public void finishCleaning(String roomNumber) {
        Room room = rooms.get(roomNumber);
        if (room == null || room.getStatus() != CleaningStatus.CLEANING) {
            System.out.println("  Cannot finish room " + roomNumber + " (must be CLEANING)");
            return;
        }
        room.setStatus(CleaningStatus.CLEAN);
        System.out.println("  Room " + roomNumber + " -> CLEAN");
    }

    public void inspect(String roomNumber) {
        Room room = rooms.get(roomNumber);
        if (room == null || room.getStatus() != CleaningStatus.CLEAN) {
            System.out.println("  Cannot inspect room " + roomNumber + " (must be CLEAN)");
            return;
        }
        room.setStatus(CleaningStatus.INSPECTED);
        activeByRoom.remove(roomNumber);
        System.out.println("  Room " + roomNumber + " -> INSPECTED (ready for guests)");
    }

    public void printBoard() {
        for (Room r : rooms.values()) {
            System.out.println("  Room " + r.getNumber() + " : " + r.getStatus());
        }
    }
}
