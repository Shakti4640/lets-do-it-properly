/*
 * Stock Trading Order Matching Engine (LLD)
 * -----------------------------------------
 * A limit order book with buy (bids) and sell (asks) sides using price-time
 * priority. Incoming orders match against the opposite side and produce trades;
 * any unfilled remainder rests on the book.
 *
 * Key classes / abstractions:
 *   - Order            : id, side, price, quantity, sequence (time priority)
 *   - Trade            : a matched execution between a buy and a sell order
 *   - OrderBook        : bids (TreeMap desc) + asks (TreeMap asc), each level FIFO
 *   - MatchingEngine   : submits orders, runs the matching loop, records trades
 *
 * Patterns used: Strategy-ish price-time ordering via TreeMap comparators,
 *                Facade (MatchingEngine over the book).
 */
import java.util.*;

public class StockTradingOrderMatchingEngineDemo {
    public static void main(String[] args) {
        MatchingEngine engine = new MatchingEngine("ACME");

        System.out.println("=== Seed resting orders ===");
        engine.submit(Side.SELL, 101, 50);
        engine.submit(Side.SELL, 102, 30);
        engine.submit(Side.SELL, 101, 20); // same price level, later in time
        engine.submit(Side.BUY, 99, 40);
        engine.submit(Side.BUY, 98, 60);
        engine.printBook();

        System.out.println("\n=== Aggressive BUY 60 @ 101 (crosses) ===");
        engine.submit(Side.BUY, 101, 60); // fills 50@101 then 10 of the 20@101
        engine.printBook();

        System.out.println("\n=== Aggressive SELL 70 @ 98 (sweeps bids) ===");
        engine.submit(Side.SELL, 98, 70); // fills 40@99 then 30 of 60@98
        engine.printBook();

        System.out.println("\n=== Trades ===");
        for (Trade t : engine.trades()) System.out.println("  " + t);
        System.out.println("Total traded qty: " + engine.totalTradedQty());
    }
}

enum Side { BUY, SELL }

class Order {
    final int id;
    final Side side;
    final int price;
    int quantity;
    final long seq;
    Order(int id, Side side, int price, int quantity, long seq) {
        this.id = id; this.side = side; this.price = price;
        this.quantity = quantity; this.seq = seq;
    }
    public String toString() {
        return "#" + id + " " + side + " " + quantity + "@" + price;
    }
}

class Trade {
    final int buyId, sellId, price, quantity;
    Trade(int buyId, int sellId, int price, int quantity) {
        this.buyId = buyId; this.sellId = sellId; this.price = price; this.quantity = quantity;
    }
    public String toString() {
        return "TRADE qty=" + quantity + " @" + price + " (buy #" + buyId + " / sell #" + sellId + ")";
    }
}

class OrderBook {
    // Bids: highest price first. Asks: lowest price first. Each level is a FIFO queue.
    final TreeMap<Integer, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    final TreeMap<Integer, Deque<Order>> asks = new TreeMap<>();

    void rest(Order o) {
        TreeMap<Integer, Deque<Order>> book = (o.side == Side.BUY) ? bids : asks;
        book.computeIfAbsent(o.price, k -> new ArrayDeque<>()).addLast(o);
    }

    Map.Entry<Integer, Deque<Order>> bestBid() { return bids.isEmpty() ? null : bids.firstEntry(); }
    Map.Entry<Integer, Deque<Order>> bestAsk() { return asks.isEmpty() ? null : asks.firstEntry(); }

    void pruneEmpty(TreeMap<Integer, Deque<Order>> book, int price) {
        Deque<Order> level = book.get(price);
        if (level != null && level.isEmpty()) book.remove(price);
    }
}

class MatchingEngine {
    private final String symbol;
    private final OrderBook book = new OrderBook();
    private final List<Trade> trades = new ArrayList<>();
    private int nextId = 1;
    private long seq = 0;

    MatchingEngine(String symbol) { this.symbol = symbol; }

    void submit(Side side, int price, int qty) {
        Order incoming = new Order(nextId++, side, price, qty, seq++);
        System.out.println("submit " + incoming);
        if (side == Side.BUY) matchBuy(incoming); else matchSell(incoming);
        if (incoming.quantity > 0) book.rest(incoming);
    }

    private void matchBuy(Order buy) {
        while (buy.quantity > 0) {
            Map.Entry<Integer, Deque<Order>> ask = book.bestAsk();
            if (ask == null || ask.getKey() > buy.price) break; // no crossing price
            Deque<Order> level = ask.getValue();
            Order resting = level.peekFirst();
            int fill = Math.min(buy.quantity, resting.quantity);
            trades.add(new Trade(buy.id, resting.id, resting.price, fill));
            buy.quantity -= fill; resting.quantity -= fill;
            if (resting.quantity == 0) level.pollFirst();
            book.pruneEmpty(book.asks, ask.getKey());
        }
    }

    private void matchSell(Order sell) {
        while (sell.quantity > 0) {
            Map.Entry<Integer, Deque<Order>> bid = book.bestBid();
            if (bid == null || bid.getKey() < sell.price) break;
            Deque<Order> level = bid.getValue();
            Order resting = level.peekFirst();
            int fill = Math.min(sell.quantity, resting.quantity);
            trades.add(new Trade(resting.id, sell.id, resting.price, fill));
            sell.quantity -= fill; resting.quantity -= fill;
            if (resting.quantity == 0) level.pollFirst();
            book.pruneEmpty(book.bids, bid.getKey());
        }
    }

    List<Trade> trades() { return trades; }

    int totalTradedQty() {
        int total = 0;
        for (Trade t : trades) total += t.quantity;
        return total;
    }

    void printBook() {
        System.out.println("--- Order Book [" + symbol + "] ---");
        System.out.println("ASKS (low->high):");
        for (Map.Entry<Integer, Deque<Order>> e : book.asks.entrySet())
            System.out.println("  " + e.getKey() + " : " + levelQty(e.getValue()) + " " + e.getValue());
        System.out.println("BIDS (high->low):");
        for (Map.Entry<Integer, Deque<Order>> e : book.bids.entrySet())
            System.out.println("  " + e.getKey() + " : " + levelQty(e.getValue()) + " " + e.getValue());
    }

    private int levelQty(Deque<Order> level) {
        int q = 0;
        for (Order o : level) q += o.quantity;
        return q;
    }
}
