/*
 * BookMyShow - Low Level Design
 * -----------------------------
 * Movie ticket booking: theaters with screens, shows, seat selection and
 * booking with a simple seat-locking guard to avoid double booking.
 *
 * Key classes:
 *   - Movie / Theater / Screen / Seat / Show
 *   - Booking
 *   - BookingService (facade with synchronized seat reservation)
 *
 * Patterns used: Facade, basic concurrency-safe reservation.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookMyShowDemo {
    public static void main(String[] args) {
        BookingService service = new BookingService();
        Movie movie = new Movie("M1", "Inception");
        Screen screen = new Screen("SCR1", 3, 3); // 9 seats
        Theater theater = new Theater("T1", "PVR", screen);
        Show show = new Show("SH1", movie, theater, "18:00");
        service.addShow(show);

        System.out.println("=== BookMyShow Demo ===");
        show.printSeats();

        Booking b1 = service.book("Alice", "SH1", new String[]{"A1","A2"});
        Booking b2 = service.book("Bob", "SH1", new String[]{"A2","A3"}); // A2 taken
        Booking b3 = service.book("Bob", "SH1", new String[]{"A3","B1"});

        show.printSeats();
        if (b1 != null) System.out.println("Alice booking: " + b1.getId() + " seats=" + b1.getSeats());
        if (b3 != null) System.out.println("Bob booking:   " + b3.getId() + " seats=" + b3.getSeats());
    }
}

class Movie {
    private final String id;
    private final String title;
    Movie(String id, String title) { this.id = id; this.title = title; }
    String getTitle() { return title; }
}

class Seat {
    private final String id;
    private boolean booked = false;
    Seat(String id) { this.id = id; }
    String getId() { return id; }
    boolean isBooked() { return booked; }
    void book() { booked = true; }
}

class Screen {
    private final String id;
    private final Map<String, Seat> seats = new HashMap<String, Seat>();
    Screen(String id, int rows, int cols) {
        this.id = id;
        for (int r = 0; r < rows; r++) {
            char rowChar = (char) ('A' + r);
            for (int c = 1; c <= cols; c++) {
                String sid = "" + rowChar + c;
                seats.put(sid, new Seat(sid));
            }
        }
    }
    Map<String, Seat> getSeats() { return seats; }
}

class Theater {
    private final String id;
    private final String name;
    private final Screen screen;
    Theater(String id, String name, Screen screen) { this.id = id; this.name = name; this.screen = screen; }
    String getName() { return name; }
    Screen getScreen() { return screen; }
}

class Show {
    private final String id;
    private final Movie movie;
    private final Theater theater;
    private final String time;
    Show(String id, Movie movie, Theater theater, String time) {
        this.id = id; this.movie = movie; this.theater = theater; this.time = time;
    }
    String getId() { return id; }
    Map<String, Seat> getSeats() { return theater.getScreen().getSeats(); }

    void printSeats() {
        System.out.println("Show " + id + " (" + movie.getTitle() + " @ " + time + " " + theater.getName() + ")");
        List<String> ids = new ArrayList<String>(getSeats().keySet());
        java.util.Collections.sort(ids);
        StringBuilder sb = new StringBuilder("  ");
        for (String sid : ids) sb.append(sid).append(getSeats().get(sid).isBooked() ? "[X] " : "[ ] ");
        System.out.println(sb.toString());
    }
}

class Booking {
    private final String id;
    private final String user;
    private final List<String> seats;
    Booking(String id, String user, List<String> seats) { this.id = id; this.user = user; this.seats = seats; }
    String getId() { return id; }
    List<String> getSeats() { return seats; }
}

class BookingService {
    private final Map<String, Show> shows = new HashMap<String, Show>();
    private int bookingSeq = 0;

    void addShow(Show show) { shows.put(show.getId(), show); }

    synchronized Booking book(String user, String showId, String[] seatIds) {
        Show show = shows.get(showId);
        if (show == null) { System.out.println("No such show"); return null; }
        // validate availability first
        for (String sid : seatIds) {
            Seat seat = show.getSeats().get(sid);
            if (seat == null) { System.out.println(user + ": invalid seat " + sid); return null; }
            if (seat.isBooked()) { System.out.println(user + ": seat " + sid + " already booked"); return null; }
        }
        List<String> booked = new ArrayList<String>();
        for (String sid : seatIds) { show.getSeats().get(sid).book(); booked.add(sid); }
        Booking booking = new Booking("BK" + (++bookingSeq), user, booked);
        System.out.println(user + " booked " + booked);
        return booking;
    }
}
