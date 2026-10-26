/*
 * Stock Exchange System - Low Level Design
 * ----------------------------------------
 * A matching engine with a price-time priority order book. Supports LIMIT
 * BUY/SELL orders, matches crossing orders and records trades.
 *
 * Key classes:
 *   - Order (BUY/SELL, price, qty)
 *   - Trade
 *   - OrderBook (per-symbol bids/asks + matching)
 *   - StockExchange (facade)
 *
 * Patterns used: Priority queues (order book), Facade.
 */

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class StockExchangeDemo {
    public static void main(String[] args) {
        StockExchange exchange = new StockExchange();
        System.out.println("=== Stock Exchange Demo ===");

        exchange.placeOrder("AAPL", Side.SELL, 150.0, 10, "seller1");
        exchange.placeOrder("AAPL", Side.SELL, 151.0, 5, "seller2");
        exchange.placeOrder("AAPL", Side.BUY, 150.5, 6, "buyer1"); // matches seller1 @150 x6
        exchange.placeOrder("AAPL", Side.BUY, 151.0, 10, "buyer2"); // matches rest of seller1 + seller2

        exchange.printBook("AAPL");
    }
}

enum Side { BUY, SELL }

class Order {
    final String id; final String symbol; final Side side; final double price;
    int quantity; final String trader; final long time;
    Order(String id, String symbol, Side side, double price, int quantity, String trader, long time) {
        this.id = id; this.symbol = symbol; this.side = side; this.price = price;
        this.quantity = quantity; this.trader = trader; this.time = time;
    }
}

class Trade {
    final String symbol; final double price; final int quantity; final String buyer; final String seller;
    Trade(String symbol, double price, int quantity, String buyer, String seller) {
        this.symbol = symbol; this.price = price; this.quantity = quantity; this.buyer = buyer; this.seller = seller;
    }
    public String toString() { return "TRADE " + quantity + " " + symbol + " @ " + price + " (" + buyer + " <- " + seller + ")"; }
}

class OrderBook {
    final String symbol;
    // bids: highest price first, then earliest time
    final PriorityQueue<Order> bids = new PriorityQueue<Order>(new Comparator<Order>() {
        public int compare(Order a, Order b) {
            if (a.price != b.price) return Double.compare(b.price, a.price);
            return Long.compare(a.time, b.time);
        }
    });
    // asks: lowest price first, then earliest time
    final PriorityQueue<Order> asks = new PriorityQueue<Order>(new Comparator<Order>() {
        public int compare(Order a, Order b) {
            if (a.price != b.price) return Double.compare(a.price, b.price);
            return Long.compare(a.time, b.time);
        }
    });

    OrderBook(String symbol) { this.symbol = symbol; }

    List<Trade> add(Order order) {
        List<Trade> trades = new ArrayList<Trade>();
        if (order.side == Side.BUY) matchBuy(order, trades);
        else matchSell(order, trades);
        return trades;
    }

    private void matchBuy(Order buy, List<Trade> trades) {
        while (buy.quantity > 0 && !asks.isEmpty() && asks.peek().price <= buy.price) {
            Order ask = asks.peek();
            int qty = Math.min(buy.quantity, ask.quantity);
            trades.add(new Trade(symbol, ask.price, qty, buy.trader, ask.trader));
            buy.quantity -= qty; ask.quantity -= qty;
            if (ask.quantity == 0) asks.poll();
        }
        if (buy.quantity > 0) bids.offer(buy);
    }

    private void matchSell(Order sell, List<Trade> trades) {
        while (sell.quantity > 0 && !bids.isEmpty() && bids.peek().price >= sell.price) {
            Order bid = bids.peek();
            int qty = Math.min(sell.quantity, bid.quantity);
            trades.add(new Trade(symbol, bid.price, qty, bid.trader, sell.trader));
            sell.quantity -= qty; bid.quantity -= qty;
            if (bid.quantity == 0) bids.poll();
        }
        if (sell.quantity > 0) asks.offer(sell);
    }
}

class StockExchange {
    private final Map<String, OrderBook> books = new HashMap<String, OrderBook>();
    private long seq = 0;

    void placeOrder(String symbol, Side side, double price, int qty, String trader) {
        OrderBook book = books.computeIfAbsent(symbol, OrderBook::new);
        Order order = new Order("O" + (++seq), symbol, side, price, qty, trader, seq);
        System.out.println(trader + " " + side + " " + qty + " " + symbol + " @ " + price);
        List<Trade> trades = book.add(order);
        for (Trade t : trades) System.out.println("  " + t);
    }

    void printBook(String symbol) {
        OrderBook book = books.get(symbol);
        System.out.println("--- Order Book " + symbol + " ---");
        System.out.println("  BIDS: " + book.bids.size() + " orders | ASKS: " + book.asks.size() + " orders");
    }
}
