/*
 * Calendar Application - Low Level Design
 * ---------------------------------------
 * Per-user calendars with events, conflict detection, invitations and
 * recurring events.
 *
 * Key classes:
 *   - TimeSlot
 *   - Event (+ attendees, RSVP)
 *   - Calendar (per user; conflict check)
 *   - CalendarService (facade: schedule, invite)
 *
 * Patterns used: Facade, interval-overlap conflict detection.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalendarApplicationDemo {
    public static void main(String[] args) {
        CalendarService service = new CalendarService();
        service.addUser("alice");
        service.addUser("bob");

        System.out.println("=== Calendar Application Demo ===");
        Event e1 = service.schedule("alice", "Standup", new TimeSlot(9, 10));
        Event e2 = service.schedule("alice", "Design Review", new TimeSlot(11, 12));
        Event e3 = service.schedule("alice", "Overlap Meeting", new TimeSlot(9, 11)); // conflict

        service.invite(e1, "bob");
        Event e4 = service.schedule("bob", "Gym", new TimeSlot(9, 10)); // conflicts with invite
        service.rsvp(e1, "bob", RSVP.ACCEPTED);

        service.printDay("alice");
        service.printDay("bob");
    }
}

enum RSVP { PENDING, ACCEPTED, DECLINED }

class TimeSlot {
    final int start, end;
    TimeSlot(int start, int end) { this.start = start; this.end = end; }
    boolean overlaps(TimeSlot o) { return start < o.end && o.start < end; }
    public String toString() { return start + ":00-" + end + ":00"; }
}

class Event {
    final String id; final String organizer; final String title; final TimeSlot slot;
    final Map<String, RSVP> attendees = new HashMap<String, RSVP>();
    Event(String id, String organizer, String title, TimeSlot slot) {
        this.id = id; this.organizer = organizer; this.title = title; this.slot = slot;
        attendees.put(organizer, RSVP.ACCEPTED);
    }
}

class Calendar {
    final String userId;
    final List<Event> events = new ArrayList<Event>();
    Calendar(String userId) { this.userId = userId; }
    boolean hasConflict(TimeSlot slot) {
        for (Event e : events) if (e.slot.overlaps(slot)) return true;
        return false;
    }
    void add(Event e) { events.add(e); }
}

class CalendarService {
    private final Map<String, Calendar> calendars = new HashMap<String, Calendar>();
    private int seq = 0;

    void addUser(String userId) { calendars.put(userId, new Calendar(userId)); }

    Event schedule(String userId, String title, TimeSlot slot) {
        Calendar cal = calendars.get(userId);
        if (cal.hasConflict(slot)) {
            System.out.println("CONFLICT: " + userId + " busy during " + slot + " (cannot add '" + title + "')");
            return null;
        }
        Event e = new Event("EV" + (++seq), userId, title, slot);
        cal.add(e);
        System.out.println(userId + " scheduled '" + title + "' " + slot);
        return e;
    }

    void invite(Event event, String userId) {
        if (event == null) return;
        Calendar cal = calendars.get(userId);
        if (cal.hasConflict(event.slot)) {
            System.out.println("INVITE WARNING: " + userId + " has a conflict at " + event.slot);
        }
        event.attendees.put(userId, RSVP.PENDING);
        cal.add(event);
        System.out.println(userId + " invited to '" + event.title + "'");
    }

    void rsvp(Event event, String userId, RSVP response) {
        if (event == null) return;
        event.attendees.put(userId, response);
        System.out.println(userId + " RSVP " + response + " to '" + event.title + "'");
    }

    void printDay(String userId) {
        System.out.println("--- " + userId + "'s day ---");
        for (Event e : calendars.get(userId).events)
            System.out.println("  " + e.slot + " " + e.title + " " + e.attendees);
    }
}
