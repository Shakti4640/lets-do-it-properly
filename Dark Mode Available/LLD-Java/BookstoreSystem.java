import java.util.*;
import java.util.regex.*;

/**
 * Represents a single book in the bookstore.
 */
class Book {
    private final String title;
    private final String author;
    private final String isbn;
    private final String content; // full text content

    public Book(String title, String author, String isbn, String content) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "Title: " + title + ", Author: " + author + ", ISBN: " + isbn;
    }
}

/**
 * Manages a collection of Book objects, keyed by ISBN.
 */
class Bookstore {
    private final Map<String, Book> books = new HashMap<>();

    /** Adds a book to the bookstore. Prevents duplicates based on ISBN. */
    public void addBook(Book book) {
        if (books.containsKey(book.getIsbn())) {
            System.out.println("Warning: Book with ISBN " + book.getIsbn() + " already exists.");
        } else {
            books.put(book.getIsbn(), book);
            System.out.println("Book '" + book.getTitle() + "' added successfully.");
        }
    }

    /** Retrieves a book from the bookstore based on its ISBN. */
    public Book getBookByIsbn(String isbn) {
        return books.get(isbn);
    }

    /** Returns a list of all books in the bookstore. */
    public List<Book> getAllBooks() {
        return new ArrayList<>(books.values());
    }
}

/**
 * Contains the logic to count occurrences of a specific word in a Book's content.
 */
class WordCounter {

    /**
     * Counts the occurrences of a specific whole word in the content of a given book.
     *
     * @param book          the Book object to analyze
     * @param word          the word to count
     * @param caseSensitive whether the counting should be case-sensitive (default false)
     * @return the number of times the word appears, or null if the book is invalid
     */
    public static Integer countWordOccurrence(Book book, String word, boolean caseSensitive) {
        if (book == null || book.getContent() == null || book.getContent().isEmpty()) {
            System.out.println("Error: Invalid book provided.");
            return null;
        }

        String content = book.getContent();
        String target = word;

        if (!caseSensitive) {
            content = content.toLowerCase();
            target = target.toLowerCase();
        }

        // Use word boundaries (\b) for whole-word matching; Pattern.quote escapes special regex chars.
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(target) + "\\b");
        Matcher matcher = pattern.matcher(content);

        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** Overload defaulting to case-insensitive counting. */
    public static Integer countWordOccurrence(Book book, String word) {
        return countWordOccurrence(book, word, false);
    }
}

/**
 * Demo / example usage.
 */
public class BookstoreSystem {
    public static void main(String[] args) {
        // Create some book objects
        Book book1 = new Book(
                "The Fellowship of the Ring",
                "J.R.R. Tolkien",
                "978-0618260274",
                "Frodo Baggins lived in Hobbiton. Frodo was a hobbit. Gandalf visited Frodo.");

        Book book2 = new Book(
                "Pride and Prejudice",
                "Jane Austen",
                "978-0141439518",
                "Elizabeth Bennet was the protagonist. Mr. Darcy admired Elizabeth.");

        Book book3 = new Book(
                "Harry Potter and the Sorcerer's Stone",
                "J.K. Rowling",
                "978-0590353403",
                "Harry Potter lived under the stairs. Harry was surprised by the letter.");

        // Create a bookstore
        Bookstore bookstore = new Bookstore();

        // Add books to the bookstore
        bookstore.addBook(book1);
        bookstore.addBook(book2);
        bookstore.addBook(book3);

        // Count word occurrences in specific books
        String isbnToSearch = "978-0618260274";
        Book bookToAnalyze = bookstore.getBookByIsbn(isbnToSearch);
        if (bookToAnalyze != null) {
            String characterToCount = "Frodo";
            Integer count = WordCounter.countWordOccurrence(bookToAnalyze, characterToCount);
            System.out.println("The word '" + characterToCount + "' appears " + count
                    + " times in '" + bookToAnalyze.getTitle() + "'.");

            String characterToCountCaseSensitive = "frodo";
            Integer countCaseSensitive = WordCounter.countWordOccurrence(
                    bookToAnalyze, characterToCountCaseSensitive, true);
            System.out.println("(Case-sensitive) The word '" + characterToCountCaseSensitive
                    + "' appears " + countCaseSensitive + " times in '" + bookToAnalyze.getTitle() + "'.");
        } else {
            System.out.println("Book with ISBN " + isbnToSearch + " not found.");
        }

        String isbnToSearch2 = "978-0141439518";
        Book bookToAnalyze2 = bookstore.getBookByIsbn(isbnToSearch2);
        if (bookToAnalyze2 != null) {
            String characterToCount2 = "Elizabeth";
            Integer count2 = WordCounter.countWordOccurrence(bookToAnalyze2, characterToCount2);
            System.out.println("The word '" + characterToCount2 + "' appears " + count2
                    + " times in '" + bookToAnalyze2.getTitle() + "'.");
        } else {
            System.out.println("Book with ISBN " + isbnToSearch2 + " not found.");
        }
    }
}