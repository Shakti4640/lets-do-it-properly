/*
 * Meeting Room Scheduler - Low Level Design
 * -----------------------------------------
 * Books meeting rooms for time intervals, prevents double-booking, and
 * supports finding an available room for a requested slot.
 *
 * Key classes:
 *   - Interval               : [start,end) with overlap check.
 *   - MeetingRoom            : room with a list of bookings.
 *   - Booking                : reservation by an organizer.
 *   - SchedulerService       : facade to find/reserve rooms.
 *
 * Patterns used: Facade, interval-overlap logic, Encapsulation.
 */

import java.util.ArrayList;
import java.util.List;

public class MeetingRoomSchedulerDemo {
    public static void main(String[] args) {
        SchedulerService scheduler = new SchedulerService();
        scheduler.addRoom(new MeetingRoom("R1", 5));
        scheduler.addRoom(new MeetingRoom("R2", 10));

        System.out.println("=== Meeting Room Scheduler Demo ===");
        Booking b1 = scheduler.book("Alice", 4, new Interval(9, 10));
        Booking b2 = scheduler.book("Bob", 8, new Interval(9, 11));   // needs cap>=8 -> R2
        Booking b3 = scheduler.book("Carol", 3, new Interval(9, 10)); // R1 busy -> R2 busy? overlaps R2 9-11
        Booking b4 = scheduler.book("Dave", 3, new Interval(10, 11)); // R1 free now
        Booking b5 = scheduler.book("Eve", 20, new Interval(12, 13)); // capacity too big
    }
}

class Interval {
    final int start;
    final int end;
    Interval(int start, int end) { this.start = start; this.end = end; }
    boolean overlaps(Interval o) { return start < o.end && o.start < end; }
    public String toString() { return "[" + start + ":00-" + end + ":00)"; }
}

class Booking {
    private final String organizer;
    private final MeetingRoom room;
    private final Interval interval;
    Booking(String organizer, MeetingRoom room, Interval interval) {
        this.organizer = organizer; this.room = room; this.interval = interval;
    }
    Interval getInterval() { return interval; }
    public String toString() { return organizer + "@" + room.getId() + interval; }
}

class MeetingRoom {
    private final String id;
    private final int capacity;
    private final List<Booking> bookings = new ArrayList<Booking>();
    MeetingRoom(String id, int capacity) { this.id = id; this.capacity = capacity; }
    String getId() { return id; }
    int getCapacity() { return capacity; }

    boolean isAvailable(Interval slot) {
        for (Booking b : bookings) if (b.getInterval().overlaps(slot)) return false;
        return true;
    }
    void addBooking(Booking b) { bookings.add(b); }
}

class SchedulerService {
    private final List<MeetingRoom> rooms = new ArrayList<MeetingRoom>();
    void addRoom(MeetingRoom r) { rooms.add(r); }

    Booking book(String organizer, int attendees, Interval slot) {
        for (MeetingRoom room : rooms) {
            if (room.getCapacity() >= attendees && room.isAvailable(slot)) {
                Booking b = new Booking(organizer, room, slot);
                room.addBooking(b);
                System.out.println("BOOKED: " + b);
                return b;
            }
        }
        System.out.println("FAILED: no room for " + organizer + " (" + attendees + " ppl) " + slot);
        return null;
    }
}
