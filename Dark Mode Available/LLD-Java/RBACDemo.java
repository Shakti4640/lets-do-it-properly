/*
 * Role-Based Access Control System (RBAC) - Low Level Design
 * ----------------------------------------------------------
 * Users are assigned roles; roles hold permissions. Supports role
 * hierarchy (a role can inherit another role's permissions) and access
 * checks against (resource, action) permissions.
 *
 * Key classes:
 *   - Permission / Role (with inheritance) / User
 *   - AccessControlService (facade: assign, checkAccess)
 *
 * Patterns used: Composite-ish role inheritance, Facade.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RBACDemo {
    public static void main(String[] args) {
        AccessControlService acs = new AccessControlService();

        Permission readDoc = new Permission("document", "read");
        Permission writeDoc = new Permission("document", "write");
        Permission deleteDoc = new Permission("document", "delete");

        Role viewer = acs.createRole("viewer");
        viewer.addPermission(readDoc);

        Role editor = acs.createRole("editor");
        editor.addPermission(writeDoc);
        editor.addParent(viewer); // editor inherits viewer

        Role admin = acs.createRole("admin");
        admin.addPermission(deleteDoc);
        admin.addParent(editor); // admin inherits editor (and viewer)

        acs.assignRole("alice", "viewer");
        acs.assignRole("bob", "admin");

        System.out.println("=== RBAC Demo ===");
        System.out.println("alice read doc:   " + acs.checkAccess("alice", "document", "read"));
        System.out.println("alice write doc:  " + acs.checkAccess("alice", "document", "write"));
        System.out.println("bob read doc:     " + acs.checkAccess("bob", "document", "read"));
        System.out.println("bob write doc:    " + acs.checkAccess("bob", "document", "write"));
        System.out.println("bob delete doc:   " + acs.checkAccess("bob", "document", "delete"));
    }
}

class Permission {
    final String resource; final String action;
    Permission(String resource, String action) { this.resource = resource; this.action = action; }
    public boolean equals(Object o) {
        if (!(o instanceof Permission)) return false;
        Permission p = (Permission) o;
        return resource.equals(p.resource) && action.equals(p.action);
    }
    public int hashCode() { return (resource + ":" + action).hashCode(); }
    public String toString() { return resource + ":" + action; }
}

class Role {
    final String name;
    private final Set<Permission> permissions = new HashSet<Permission>();
    private final List<Role> parents = new ArrayList<Role>();
    Role(String name) { this.name = name; }
    void addPermission(Permission p) { permissions.add(p); }
    void addParent(Role r) { parents.add(r); }

    boolean hasPermission(Permission p) {
        if (permissions.contains(p)) return true;
        for (Role parent : parents) if (parent.hasPermission(p)) return true;
        return false;
    }
}

class User {
    final String name;
    final Set<Role> roles = new HashSet<Role>();
    User(String name) { this.name = name; }
}

class AccessControlService {
    private final Map<String, Role> roles = new HashMap<String, Role>();
    private final Map<String, User> users = new HashMap<String, User>();

    Role createRole(String name) {
        Role r = new Role(name);
        roles.put(name, r);
        return r;
    }

    void assignRole(String username, String roleName) {
        User u = users.computeIfAbsent(username, User::new);
        Role r = roles.get(roleName);
        if (r != null) { u.roles.add(r); System.out.println("Assigned role '" + roleName + "' to " + username); }
    }

    boolean checkAccess(String username, String resource, String action) {
        User u = users.get(username);
        if (u == null) return false;
        Permission needed = new Permission(resource, action);
        for (Role r : u.roles) if (r.hasPermission(needed)) return true;
        return false;
    }
}
