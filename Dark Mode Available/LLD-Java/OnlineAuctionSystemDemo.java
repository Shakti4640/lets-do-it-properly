/*
 * Online Auction System - Low Level Design
 * ----------------------------------------
 * Sellers list auction items with a starting price and minimum increment.
 * Bidders place bids; whenever a bid is outbid, the previous high bidder is
 * notified via the Observer pattern. Closing an auction selects the highest
 * valid bid as the winner.
 *
 * Key classes / abstractions:
 *   - AuctionItem        : subject; tracks high bid and observers
 *   - Bidder             : observer that reacts to outbid notifications
 *   - Bid                : amount + bidder
 *   - AuctionHouse       : facade for listing, bidding, closing
 *
 * Patterns used: Observer (outbid notifications), Facade (AuctionHouse).
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnlineAuctionSystemDemo {
    public static void main(String[] args) {
        AuctionHouse house = new AuctionHouse();

        Bidder alice = new Bidder("alice");
        Bidder bob = new Bidder("bob");
        Bidder carol = new Bidder("carol");

        System.out.println("=== Online Auction System Demo ===");
        String itemId = house.listItem("Vintage Watch", 10000, 1000); // start $100, min +$10

        house.placeBid(itemId, alice, 10000);
        house.placeBid(itemId, bob, 11000);
        house.placeBid(itemId, carol, 12500);
        house.placeBid(itemId, alice, 13500);

        System.out.println("\n--- Invalid bid attempts ---");
        house.placeBid(itemId, bob, 13800);  // below min increment over 13500
        house.placeBid(itemId, bob, 14500);  // valid

        System.out.println("\n--- Closing auction ---");
        house.closeAuction(itemId);

        System.out.println("\n--- Post-close bid rejected ---");
        house.placeBid(itemId, carol, 20000);

        System.out.println("\n--- Notification log ---");
        alice.printNotifications();
        bob.printNotifications();
        carol.printNotifications();
    }
}

interface BidObserver {
    void onOutbid(String itemName, long yourBid, long newHighBid, String newLeader);
    void onWon(String itemName, long amount);
    String getName();
}

class Bidder implements BidObserver {
    private final String name;
    private final List<String> notifications = new ArrayList<String>();

    Bidder(String name) { this.name = name; }

    public String getName() { return name; }

    public void onOutbid(String itemName, long yourBid, long newHighBid, String newLeader) {
        String msg = "You were outbid on '" + itemName + "' (your "
                + money(yourBid) + " < " + money(newHighBid) + " by " + newLeader + ")";
        notifications.add(msg);
        System.out.println("  -> notify " + name + ": " + msg);
    }

    public void onWon(String itemName, long amount) {
        String msg = "You WON '" + itemName + "' for " + money(amount);
        notifications.add(msg);
        System.out.println("  -> notify " + name + ": " + msg);
    }

    void printNotifications() {
        System.out.println(name + " received " + notifications.size() + " notification(s)");
        for (String n : notifications) System.out.println("    - " + n);
    }

    static String money(long cents) { return String.format("$%,.2f", cents / 100.0); }
}

class Bid {
    final Bidder bidder;
    final long amountCents;
    Bid(Bidder bidder, long amountCents) {
        this.bidder = bidder;
        this.amountCents = amountCents;
    }
}

class AuctionItem {
    final String id;
    final String name;
    final long startingPriceCents;
    final long minIncrementCents;
    Bid highBid = null;
    boolean open = true;
    final Set<BidObserver> observers = new LinkedHashSet<BidObserver>();

    AuctionItem(String id, String name, long startingPriceCents, long minIncrementCents) {
        this.id = id;
        this.name = name;
        this.startingPriceCents = startingPriceCents;
        this.minIncrementCents = minIncrementCents;
    }

    long minimumAcceptableBid() {
        if (highBid == null) return startingPriceCents;
        return highBid.amountCents + minIncrementCents;
    }
}

class AuctionHouse {
    private final Map<String, AuctionItem> items = new HashMap<String, AuctionItem>();
    private int nextId = 1;

    String listItem(String name, long startingPriceCents, long minIncrementCents) {
        String id = "ITEM-" + (nextId++);
        items.put(id, new AuctionItem(id, name, startingPriceCents, minIncrementCents));
        System.out.println("Listed " + id + " '" + name + "' start "
                + Bidder.money(startingPriceCents) + " (min +" + Bidder.money(minIncrementCents) + ")");
        return id;
    }

    private AuctionItem require(String itemId) {
        AuctionItem item = items.get(itemId);
        if (item == null) throw new IllegalArgumentException("No such item: " + itemId);
        return item;
    }

    void placeBid(String itemId, Bidder bidder, long amountCents) {
        AuctionItem item = require(itemId);
        if (!item.open) {
            System.out.println("REJECTED: auction for '" + item.name + "' is closed");
            return;
        }
        long minBid = item.minimumAcceptableBid();
        if (amountCents < minBid) {
            System.out.println("REJECTED: " + bidder.getName() + " bid "
                    + Bidder.money(amountCents) + " < required " + Bidder.money(minBid));
            return;
        }

        Bid previous = item.highBid;
        item.highBid = new Bid(bidder, amountCents);
        item.observers.add(bidder);
        System.out.println(bidder.getName() + " bids " + Bidder.money(amountCents)
                + " on '" + item.name + "' (now leading)");

        if (previous != null && previous.bidder != bidder) {
            previous.bidder.onOutbid(item.name, previous.amountCents, amountCents, bidder.getName());
        }
    }

    void closeAuction(String itemId) {
        AuctionItem item = require(itemId);
        item.open = false;
        if (item.highBid == null) {
            System.out.println("'" + item.name + "' closed with no bids");
            return;
        }
        System.out.println("'" + item.name + "' closed. Winner: "
                + item.highBid.bidder.getName() + " at " + Bidder.money(item.highBid.amountCents));
        item.highBid.bidder.onWon(item.name, item.highBid.amountCents);
    }
}
