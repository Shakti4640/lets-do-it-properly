/*
 * Real Estate Listing Platform - Low Level Design
 * --------------------------------------------------------------
 * Agents post property listings; buyers search and filter by city, price
 * range, and bedroom count. Listings can be marked SOLD and results can be
 * sorted by price or newest. Filtering uses composable predicate criteria.
 *
 * Key classes / abstractions:
 *   - Property            : a listing with attributes + status.
 *   - Agent               : owns listings.
 *   - SearchCriteria      : composable filters (Specification pattern).
 *   - ListingService      : add, search, sort, and mark sold.
 *
 * Patterns used:
 *   - Specification (composable search criteria via Predicate)
 *   - Strategy      (sort comparators)
 */

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RealEstateListingPlatformDemo {
    public static void main(String[] args) {
        ListingService service = new ListingService();
        Agent agent = new Agent("AG-1", "Priya Sharma");

        service.addListing(agent, new Property("P1", "Bengaluru", 9500000, 3, "Whitefield 3BHK"));
        service.addListing(agent, new Property("P2", "Bengaluru", 6500000, 2, "Indiranagar 2BHK"));
        service.addListing(agent, new Property("P3", "Mumbai", 18000000, 3, "Bandra 3BHK"));
        service.addListing(agent, new Property("P4", "Bengaluru", 12000000, 4, "HSR 4BHK villa"));
        service.addListing(agent, new Property("P5", "Pune", 5500000, 2, "Kothrud 2BHK"));

        System.out.println("=== Filter: Bengaluru, price <= 1.0Cr, >= 3 beds ===");
        SearchCriteria criteria = new SearchCriteria()
                .inCity("Bengaluru")
                .maxPrice(10000000)
                .minBedrooms(3);
        List<Property> results = service.search(criteria, Sort.PRICE_ASC);
        print(results);

        System.out.println("\n=== Mark P1 as SOLD, search again ===");
        service.markSold("P1");
        print(service.search(criteria, Sort.PRICE_ASC));

        System.out.println("\n=== All available, sorted by price descending ===");
        print(service.search(new SearchCriteria(), Sort.PRICE_DESC));

        System.out.println("\n=== Bedrooms >= 3 anywhere, newest first ===");
        print(service.search(new SearchCriteria().minBedrooms(3), Sort.NEWEST));
    }

    private static void print(List<Property> list) {
        if (list.isEmpty()) {
            System.out.println("  (no matches)");
            return;
        }
        for (Property p : list) System.out.println("  " + p);
    }
}

enum Sort { PRICE_ASC, PRICE_DESC, NEWEST }

class Property {
    private final String id;
    private final String city;
    private final long price;
    private final int bedrooms;
    private final String title;
    private boolean sold = false;
    private int sequence; // assigned by service to model recency

    public Property(String id, String city, long price, int bedrooms, String title) {
        this.id = id;
        this.city = city;
        this.price = price;
        this.bedrooms = bedrooms;
        this.title = title;
    }

    public String getId() { return id; }
    public String getCity() { return city; }
    public long getPrice() { return price; }
    public int getBedrooms() { return bedrooms; }
    public boolean isSold() { return sold; }
    public void markSold() { this.sold = true; }
    public int getSequence() { return sequence; }
    public void setSequence(int s) { this.sequence = s; }

    @Override
    public String toString() {
        return id + " | " + title + " | " + city + " | Rs" + price + " | " + bedrooms + "BHK"
                + (sold ? " | SOLD" : "");
    }
}

class Agent {
    private final String id;
    private final String name;
    private final List<String> listingIds = new ArrayList<>();

    public Agent(String id, String name) { this.id = id; this.name = name; }
    public String getName() { return name; }
    public void addListing(String propertyId) { listingIds.add(propertyId); }
}

class SearchCriteria {
    private final List<Predicate<Property>> filters = new ArrayList<>();

    public SearchCriteria inCity(String city) {
        filters.add(p -> p.getCity().equalsIgnoreCase(city));
        return this;
    }
    public SearchCriteria maxPrice(long max) {
        filters.add(p -> p.getPrice() <= max);
        return this;
    }
    public SearchCriteria minBedrooms(int min) {
        filters.add(p -> p.getBedrooms() >= min);
        return this;
    }

    public boolean matches(Property p) {
        for (Predicate<Property> f : filters) {
            if (!f.test(p)) return false;
        }
        return true;
    }
}

class ListingService {
    private final Map<String, Property> listings = new LinkedHashMap<>();
    private int seq = 0;

    public void addListing(Agent agent, Property property) {
        property.setSequence(++seq);
        listings.put(property.getId(), property);
        agent.addListing(property.getId());
        System.out.println("Listed " + property.getId() + " by " + agent.getName());
    }

    public void markSold(String propertyId) {
        Property p = listings.get(propertyId);
        if (p != null) {
            p.markSold();
            System.out.println("Marked " + propertyId + " SOLD");
        }
    }

    public List<Property> search(SearchCriteria criteria, Sort sort) {
        List<Property> matches = listings.values().stream()
                .filter(p -> !p.isSold())
                .filter(criteria::matches)
                .collect(Collectors.toList());

        Comparator<Property> cmp;
        switch (sort) {
            case PRICE_ASC:
                cmp = Comparator.comparingLong(Property::getPrice);
                break;
            case PRICE_DESC:
                cmp = Comparator.comparingLong(Property::getPrice).reversed();
                break;
            case NEWEST:
            default:
                cmp = Comparator.comparingInt(Property::getSequence).reversed();
                break;
        }
        matches.sort(cmp);
        return matches;
    }
}
