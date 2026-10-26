/*
 * Learning Management System - Low Level Design
 * ---------------------------------------------
 * Courses with lessons, student enrollment, progress tracking and
 * certificate issuance on completion.
 *
 * Key classes:
 *   - Course / Lesson
 *   - Student / Enrollment (progress)
 *   - LMS (facade)
 *
 * Patterns used: Facade, progress aggregation, Encapsulation.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LearningManagementDemo {
    public static void main(String[] args) {
        LMS lms = new LMS();
        Course course = lms.createCourse("C1", "Java Basics");
        course.addLesson(new Lesson("L1", "Variables"));
        course.addLesson(new Lesson("L2", "Loops"));
        course.addLesson(new Lesson("L3", "OOP"));

        Student alice = new Student("S1", "Alice");
        lms.enroll(alice, "C1");

        System.out.println("=== Learning Management Demo ===");
        lms.completeLesson(alice, "C1", "L1");
        lms.completeLesson(alice, "C1", "L2");
        System.out.println("Progress: " + lms.progress(alice, "C1") + "%");
        lms.completeLesson(alice, "C1", "L3");
        System.out.println("Progress: " + lms.progress(alice, "C1") + "%");
        lms.issueCertificate(alice, "C1");
    }
}

class Lesson {
    final String id; final String title;
    Lesson(String id, String title) { this.id = id; this.title = title; }
}

class Course {
    final String id; final String title;
    final List<Lesson> lessons = new ArrayList<Lesson>();
    Course(String id, String title) { this.id = id; this.title = title; }
    void addLesson(Lesson l) { lessons.add(l); }
}

class Student {
    final String id; final String name;
    Student(String id, String name) { this.id = id; this.name = name; }
}

class Enrollment {
    final Student student; final Course course;
    final Set<String> completedLessons = new HashSet<String>();
    boolean certified = false;
    Enrollment(Student student, Course course) { this.student = student; this.course = course; }
    int progressPercent() {
        if (course.lessons.isEmpty()) return 0;
        return completedLessons.size() * 100 / course.lessons.size();
    }
}

class LMS {
    private final Map<String, Course> courses = new HashMap<String, Course>();
    private final Map<String, Enrollment> enrollments = new HashMap<String, Enrollment>();

    private String key(Student s, String courseId) { return s.id + ":" + courseId; }

    Course createCourse(String id, String title) {
        Course c = new Course(id, title);
        courses.put(id, c);
        return c;
    }

    void enroll(Student s, String courseId) {
        Course c = courses.get(courseId);
        enrollments.put(key(s, courseId), new Enrollment(s, c));
        System.out.println(s.name + " enrolled in '" + c.title + "'");
    }

    void completeLesson(Student s, String courseId, String lessonId) {
        Enrollment e = enrollments.get(key(s, courseId));
        if (e == null) { System.out.println("Not enrolled"); return; }
        e.completedLessons.add(lessonId);
        System.out.println(s.name + " completed lesson " + lessonId);
    }

    int progress(Student s, String courseId) {
        Enrollment e = enrollments.get(key(s, courseId));
        return e == null ? 0 : e.progressPercent();
    }

    void issueCertificate(Student s, String courseId) {
        Enrollment e = enrollments.get(key(s, courseId));
        if (e != null && e.progressPercent() == 100) {
            e.certified = true;
            System.out.println("CERTIFICATE issued to " + s.name + " for '" + e.course.title + "'");
        } else {
            System.out.println("Cannot certify " + s.name + " - course incomplete");
        }
    }
}
