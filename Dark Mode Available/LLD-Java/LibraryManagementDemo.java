/*
 * Library Management System - Low Level Design
 * --------------------------------------------
 * Members borrow/return book copies; supports search and fines.
 *
 * Key classes:
 *   - Book / BookItem (copy) : a title vs a physical copy with barcode.
 *   - Member / Librarian     : actors.
 *   - Catalog                : search by title/author.
 *   - Loan                   : a borrowing record with due date + fine.
 *   - Library                : facade coordinating everything.
 *
 * Patterns used: Facade (Library), Encapsulation, simple Strategy for fines.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LibraryManagementDemo {
    public static void main(String[] args) {
        Library lib = new Library();
        Book b1 = new Book("ISBN1", "Clean Code", "Robert Martin");
        Book b2 = new Book("ISBN2", "Effective Java", "Joshua Bloch");
        lib.addBookCopies(b1, 2);
        lib.addBookCopies(b2, 1);

        Member alice = new Member("M1", "Alice");
        Member bob = new Member("M2", "Bob");
        lib.registerMember(alice);
        lib.registerMember(bob);

        System.out.println("=== Library Demo ===");
        System.out.println("Search 'Java': " + lib.searchByTitle("Java"));

        Loan l1 = lib.checkout(alice, "ISBN2");
        Loan l2 = lib.checkout(bob, "ISBN2"); // none left
        lib.checkout(alice, "ISBN1");

        if (l1 != null) {
            double fine = lib.returnBook(l1, 20); // 20 days, due in 14
            System.out.println("Alice fine: $" + fine);
        }
        // Now bob can get ISBN2
        lib.checkout(bob, "ISBN2");
    }
}

class Book {
    private final String isbn;
    private final String title;
    private final String author;
    Book(String isbn, String title, String author) { this.isbn = isbn; this.title = title; this.author = author; }
    String getIsbn() { return isbn; }
    String getTitle() { return title; }
    String getAuthor() { return author; }
    public String toString() { return title + " by " + author; }
}

class BookItem {
    private final String barcode;
    private final Book book;
    private boolean available = true;
    BookItem(String barcode, Book book) { this.barcode = barcode; this.book = book; }
    String getBarcode() { return barcode; }
    Book getBook() { return book; }
    boolean isAvailable() { return available; }
    void setAvailable(boolean a) { available = a; }
}

class Member {
    private final String id;
    private final String name;
    private final List<Loan> loans = new ArrayList<Loan>();
    Member(String id, String name) { this.id = id; this.name = name; }
    String getId() { return id; }
    String getName() { return name; }
    List<Loan> getLoans() { return loans; }
}

class Loan {
    private final BookItem item;
    private final Member member;
    private final int loanDays = 14;
    Loan(BookItem item, Member member) { this.item = item; this.member = member; }
    BookItem getItem() { return item; }
    Member getMember() { return member; }
    int getLoanDays() { return loanDays; }
}

class Library {
    private final Map<String, Book> books = new HashMap<String, Book>();
    private final Map<String, List<BookItem>> copies = new HashMap<String, List<BookItem>>();
    private final Map<String, Member> members = new HashMap<String, Member>();
    private final double finePerDay = 2.0;
    private int barcodeSeq = 0;

    void addBookCopies(Book book, int count) {
        books.put(book.getIsbn(), book);
        copies.putIfAbsent(book.getIsbn(), new ArrayList<BookItem>());
        for (int i = 0; i < count; i++) {
            copies.get(book.getIsbn()).add(new BookItem("BC" + (++barcodeSeq), book));
        }
    }
    void registerMember(Member m) { members.put(m.getId(), m); }

    List<Book> searchByTitle(String keyword) {
        List<Book> result = new ArrayList<Book>();
        for (Book b : books.values()) if (b.getTitle().toLowerCase().contains(keyword.toLowerCase())) result.add(b);
        return result;
    }

    Loan checkout(Member member, String isbn) {
        List<BookItem> items = copies.get(isbn);
        if (items == null) { System.out.println("No such book: " + isbn); return null; }
        for (BookItem item : items) {
            if (item.isAvailable()) {
                item.setAvailable(false);
                Loan loan = new Loan(item, member);
                member.getLoans().add(loan);
                System.out.println(member.getName() + " checked out " + item.getBook().getTitle());
                return loan;
            }
        }
        System.out.println("No copies available of " + books.get(isbn).getTitle());
        return null;
    }

    double returnBook(Loan loan, int daysHeld) {
        loan.getItem().setAvailable(true);
        loan.getMember().getLoans().remove(loan);
        int overdue = Math.max(0, daysHeld - loan.getLoanDays());
        double fine = overdue * finePerDay;
        System.out.println(loan.getMember().getName() + " returned " + loan.getItem().getBook().getTitle()
                + (overdue > 0 ? " (" + overdue + " days late)" : " (on time)"));
        return fine;
    }
}
