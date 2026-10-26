/*
 * Online Hotel Booking System - Low Level Design
 * ----------------------------------------------
 * Search hotels, check room availability for a date range, and reserve.
 *
 * Key classes:
 *   - Hotel / Room (RoomType)
 *   - DateRange
 *   - Reservation
 *   - HotelBookingService (facade with availability check)
 *
 * Patterns used: Facade, interval-overlap availability, Encapsulation.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HotelBookingDemo {
    public static void main(String[] args) {
        HotelBookingService service = new HotelBookingService();
        Hotel hotel = new Hotel("H1", "Grand Plaza", "Bangalore");
        hotel.addRoom(new Room("101", RoomType.DELUXE, 5000));
        hotel.addRoom(new Room("102", RoomType.DELUXE, 5000));
        hotel.addRoom(new Room("201", RoomType.SUITE, 9000));
        service.addHotel(hotel);

        System.out.println("=== Hotel Booking Demo ===");
        System.out.println("Hotels in Bangalore: " + service.searchByCity("Bangalore"));

        DateRange r1 = new DateRange(10, 13); // day 10..13
        Reservation res1 = service.book("Alice", "H1", RoomType.DELUXE, r1);
        Reservation res2 = service.book("Bob", "H1", RoomType.DELUXE, new DateRange(11, 12)); // 2nd deluxe
        Reservation res3 = service.book("Carol", "H1", RoomType.DELUXE, new DateRange(12, 14)); // none free overlap

        if (res1 != null) System.out.println("Alice -> room " + res1.getRoom().getNumber());
        if (res2 != null) System.out.println("Bob   -> room " + res2.getRoom().getNumber());
    }
}

enum RoomType { STANDARD, DELUXE, SUITE }

class DateRange {
    final int checkIn, checkOut;
    DateRange(int checkIn, int checkOut) { this.checkIn = checkIn; this.checkOut = checkOut; }
    boolean overlaps(DateRange o) { return checkIn < o.checkOut && o.checkIn < checkOut; }
    public String toString() { return "[" + checkIn + ".." + checkOut + "]"; }
}

class Room {
    private final String number;
    private final RoomType type;
    private final double pricePerNight;
    private final List<DateRange> bookedRanges = new ArrayList<DateRange>();
    Room(String number, RoomType type, double pricePerNight) {
        this.number = number; this.type = type; this.pricePerNight = pricePerNight;
    }
    String getNumber() { return number; }
    RoomType getType() { return type; }
    double getPrice() { return pricePerNight; }
    boolean isAvailable(DateRange range) {
        for (DateRange b : bookedRanges) if (b.overlaps(range)) return false;
        return true;
    }
    void reserve(DateRange range) { bookedRanges.add(range); }
}

class Hotel {
    final String id; final String name; final String city;
    private final List<Room> rooms = new ArrayList<Room>();
    Hotel(String id, String name, String city) { this.id = id; this.name = name; this.city = city; }
    String getCity() { return city; }
    void addRoom(Room r) { rooms.add(r); }
    List<Room> getRooms() { return rooms; }
    public String toString() { return name + " (" + city + ")"; }
}

class Reservation {
    private final String id; private final String guest; private final Room room; private final DateRange range;
    Reservation(String id, String guest, Room room, DateRange range) {
        this.id = id; this.guest = guest; this.room = room; this.range = range;
    }
    Room getRoom() { return room; }
}

class HotelBookingService {
    private final Map<String, Hotel> hotels = new HashMap<String, Hotel>();
    private int resSeq = 0;

    void addHotel(Hotel h) { hotels.put(h.id, h); }

    List<Hotel> searchByCity(String city) {
        List<Hotel> result = new ArrayList<Hotel>();
        for (Hotel h : hotels.values()) if (h.getCity().equalsIgnoreCase(city)) result.add(h);
        return result;
    }

    Reservation book(String guest, String hotelId, RoomType type, DateRange range) {
        Hotel hotel = hotels.get(hotelId);
        if (hotel == null) { System.out.println("No hotel: " + hotelId); return null; }
        for (Room room : hotel.getRooms()) {
            if (room.getType() == type && room.isAvailable(range)) {
                room.reserve(range);
                Reservation res = new Reservation("RSV" + (++resSeq), guest, room, range);
                System.out.println("BOOKED " + guest + " " + type + " room " + room.getNumber() + " " + range
                        + " (Rs " + room.getPrice() + "/night)");
                return res;
            }
        }
        System.out.println("FAILED: no " + type + " available for " + guest + " " + range);
        return null;
    }
}
