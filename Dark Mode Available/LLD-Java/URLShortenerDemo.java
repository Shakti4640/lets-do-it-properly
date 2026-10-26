/*
 * URL Shortener - Low Level Design
 * --------------------------------
 * Encodes long URLs into short codes using base62 over an incrementing id,
 * supports custom aliases, expansion, and basic click tracking.
 *
 * Key classes:
 *   - Base62Encoder
 *   - UrlMapping
 *   - UrlShortenerService (facade + in-memory store)
 *
 * Patterns used: Strategy-ish encoder, Facade.
 */

import java.util.HashMap;
import java.util.Map;

public class URLShortenerDemo {
    public static void main(String[] args) {
        UrlShortenerService service = new UrlShortenerService("https://sho.rt/");
        System.out.println("=== URL Shortener Demo ===");

        String s1 = service.shorten("https://www.example.com/some/very/long/path?a=1");
        String s2 = service.shorten("https://openai.com");
        String s3 = service.shortenCustom("https://github.com/torvalds/linux", "linux");

        System.out.println("Short 1: " + s1);
        System.out.println("Short 2: " + s2);
        System.out.println("Custom : " + s3);

        System.out.println("Expand 1: " + service.expand(s1));
        System.out.println("Expand custom: " + service.expand(s3));
        service.expand(s3); // another click
        System.out.println("Clicks on custom: " + service.getClicks(s3));

        System.out.println("Duplicate custom: " + service.shortenCustom("https://x.com", "linux"));
    }
}

class Base62Encoder {
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static String encode(long id) {
        if (id == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }
}

class UrlMapping {
    final String code;
    final String longUrl;
    int clicks = 0;
    UrlMapping(String code, String longUrl) { this.code = code; this.longUrl = longUrl; }
}

class UrlShortenerService {
    private final String baseDomain;
    private final Map<String, UrlMapping> codeToUrl = new HashMap<String, UrlMapping>();
    private long counter = 1000;

    UrlShortenerService(String baseDomain) { this.baseDomain = baseDomain; }

    String shorten(String longUrl) {
        String code = Base62Encoder.encode(++counter);
        codeToUrl.put(code, new UrlMapping(code, longUrl));
        return baseDomain + code;
    }

    String shortenCustom(String longUrl, String alias) {
        if (codeToUrl.containsKey(alias)) {
            return "ERROR: alias '" + alias + "' already taken";
        }
        codeToUrl.put(alias, new UrlMapping(alias, longUrl));
        return baseDomain + alias;
    }

    String expand(String shortUrl) {
        String code = shortUrl.replace(baseDomain, "");
        UrlMapping m = codeToUrl.get(code);
        if (m == null) return "ERROR: unknown code";
        m.clicks++;
        return m.longUrl;
    }

    int getClicks(String shortUrl) {
        UrlMapping m = codeToUrl.get(shortUrl.replace(baseDomain, ""));
        return m == null ? -1 : m.clicks;
    }
}
