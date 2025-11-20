package src;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;

public class ServiceRequestSystem {
    // ====== Constants / Config ======
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] STATUSES = {"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"};
    private static final String[] PRIORITIES = {"CRITICAL", "HIGH", "MEDIUM", "LOW"};
    private static final String[] CATEGORY_GROUPS = {
            "IT Support - Hardware",
            "IT Support - Software",
            "IT Support - Network",
            "Facilities - Maintenance",
            "Facilities - Repairs",
            "Facilities - Access",
            "HR Services - Benefits",
            "HR Services - Payroll",
            "HR Services - Policies",
            "General Services - Supplies",
            "General Services - Equipment",
            "General Services - Other"
    };
    private static final String ADMIN_PIN = "1234"; // simple admin gate for demo

    // ====== Program State ======
    private static final Scanner scanner = new Scanner(System.in);
    private static final RequestManager requestManager = new RequestManager();
    private static final ReportGenerator reportGenerator = new ReportGenerator(requestManager);
    private static final FileHandler fileHandler = new FileHandler(requestManager);

    public static void main(String[] args) {
        printBanner();
        try {
            fileHandler.loadData();
            if (requestManager.getUsers().isEmpty()) {
                seedSampleData();
                fileHandler.saveData();
            }
        } catch (Exception e) {
            System.out.println("[WARN] Failed to load data: " + e.getMessage());
        }
        mainMenu();
        // Auto-save on exit
        try {
            fileHandler.saveData();
            System.out.println("\nData saved. Goodbye!");
        } catch (Exception e) {
            System.out.println("[WARN] Failed to save data on exit: " + e.getMessage());
        }
    }

    private static void printBanner() {
        System.out.println("=== Service Request Management System ===");
        System.out.println("Single-file Java console app (no external deps)\n");
    }

    private static void mainMenu() {
        while (true) {
            System.out.println("\n=== Main Menu ===");
            System.out.println("1. Submit New Service Request");
            System.out.println("2. View My Requests");
            System.out.println("3. Administrator Panel (Admin Only)");
            System.out.println("4. Generate Reports");
            System.out.println("5. Data Management");
            System.out.println("6. Help & Documentation");
            System.out.println("7. Exit System");
            System.out.print("\nEnter your choice: ");
            int choice = readIntInRange(1, 7);
            switch (choice) {
                case 1:
                    submitNewRequestFlow();
                    break;
                case 2:
                    viewMyRequestsFlow();
                    break;
                case 3:
                    adminPanel();
                    break;
                case 4:
                    reportsMenu();
                    break;
                case 5:
                    dataManagementMenu();
                    break;
                case 6:
                    helpMenu();
                    break;
                case 7:
                    return;
                default:
                    break;
            }
        }
    }

    // ====== Menu Flows ======
    private static void submitNewRequestFlow() {
        System.out.println("\n=== New Service Request ===");
        String ticketIdPreview = requestManager.previewNextTicketId();
        System.out.println("Ticket ID: " + ticketIdPreview + " (Auto-generated)\n");

        System.out.println("User Information:");
        String name = prompt("Name");
        String dept = prompt("Department");
        String email = prompt("Email");
        String phone = prompt("Phone");

        System.out.println("\nRequest Details:");
        String category = pickFromList("Category", CATEGORY_GROUPS);
        String priority = pickFromList("Priority", PRIORITIES);
        String subject = prompt("Subject");
        String description = promptMultiline("Description (end with a single '.' on a new line)");

        System.out.print("\nSubmit Request? (Y/N): ");
        if (!yesNo()) {
            System.out.println("Cancelled.");
            return;
        }

        User user = requestManager.findOrCreateUserByEmail(email, name, dept, "USER", phone);
        ServiceRequest req = requestManager.createRequest(user, category, priority, subject, description);
        System.out.println("\nRequest submitted successfully!");
        System.out.println("Your Ticket ID: " + req.ticketId + " (save this for reference)\n");
        System.out.println(req.toDisplayString());
        pause();
    }

    private static void viewMyRequestsFlow() {
        System.out.println("\n=== View My Requests ===");
        String email = prompt("Enter your Email");
        Optional<User> userOpt = requestManager.findUserByEmail(email);
        if (userOpt.isEmpty()) {
            System.out.println("User not found. Create a new account?");
            System.out.print("(Y/N): ");
            if (!yesNo()) return;
            String name = prompt("Name");
            String dept = prompt("Department");
            String phone = prompt("Phone");
            User newUser = requestManager.createUser(name, dept, "USER", email, phone);
            System.out.println("User created with ID: " + newUser.userId);
        }
        User user = requestManager.findUserByEmail(email).get();

        while (true) {
            System.out.println("\n=== My Dashboard (" + user.name + ") ===");
            System.out.println("1. List My Requests");
            System.out.println("2. Search My Requests by Keyword");
            System.out.println("3. Add Follow-up Comment to a Request");
            System.out.println("4. View Assigned to Me (if Agent)");
            System.out.println("5. Back to Main Menu");
            System.out.print("Enter choice: ");
            int choice = readIntInRange(1, 5);
            switch (choice) {
                case 1:
                    listRequestsForUser(user);
                    break;
                case 2:
                    searchRequestsForUser(user);
                    break;
                case 3:
                    addCommentForUserFlow(user);
                    break;
                case 4:
                    viewAssignedToAgent(user);
                    break;
                case 5:
                    return;
                default:
                    break;
            }
        }
    }

    private static void adminPanel() {
        System.out.print("\nEnter Admin PIN: ");
        String pin = scanner.nextLine().trim();
        if (!ADMIN_PIN.equals(pin)) {
            System.out.println("Invalid PIN.");
            return;
        }
        while (true) {
            System.out.println("\n=== Administrator Panel ===");
            System.out.println("1. View/Manage All Requests");
            System.out.println("2. Assign Request to Agent");
            System.out.println("3. Update Request Status");
            System.out.println("4. Add Admin/Agent/User");
            System.out.println("5. Manage Users (List/Delete)");
            System.out.println("6. Export Request Details to Text");
            System.out.println("7. Back");
            System.out.print("Enter choice: ");
            int choice = readIntInRange(1, 7);
            switch (choice) {
                case 1:
                    manageAllRequestsFlow();
                    break;
                case 2:
                    assignRequestFlow();
                    break;
                case 3:
                    updateRequestStatusFlow();
                    break;
                case 4:
                    createUserFlow();
                    break;
                case 5:
                    manageUsersMenu();
                    break;
                case 6:
                    exportSingleRequestFlow();
                    break;
                case 7:
                    return;
                default:
                    break;
            }
        }
    }

    private static void reportsMenu() {
        while (true) {
            System.out.println("\n=== Reports ===");
            System.out.println("1. Summary Statistics");
            System.out.println("2. Requests by Category");
            System.out.println("3. Requests by Priority");
            System.out.println("4. Average Resolution Time");
            System.out.println("5. Export All Requests to CSV");
            System.out.println("6. Back");
            System.out.print("Enter choice: ");
            int choice = readIntInRange(1, 6);
            switch (choice) {
                case 1:
                    reportGenerator.printSummaryStatistics();
                    break;
                case 2:
                    reportGenerator.printByCategory();
                    break;
                case 3:
                    reportGenerator.printByPriority();
                    break;
                case 4:
                    reportGenerator.printAverageResolutionTime();
                    break;
                case 5:
                    try {
                        Path p = fileHandler.exportAllRequestsCsv();
                        System.out.println("Exported to: " + p.toAbsolutePath());
                    } catch (Exception e) {
                        System.out.println("[ERROR] Export failed: " + e.getMessage());
                    }
                    break;
                case 6:
                    return;
                default:
                    break;
            }
            pause();
        }
    }

    private static void dataManagementMenu() {
        while (true) {
            System.out.println("\n=== Data Management ===");
            System.out.println("1. Save Data");
            System.out.println("2. Load Data");
            System.out.println("3. Create Backup (timestamped)");
            System.out.println("4. Restore from Latest Backup");
            System.out.println("5. Back");
            System.out.print("Enter choice: ");
            int choice = readIntInRange(1, 5);
            try {
                switch (choice) {
                    case 1:
                        fileHandler.saveData();
                        System.out.println("Saved.");
                        break;
                    case 2:
                        fileHandler.loadData();
                        System.out.println("Loaded.");
                        break;
                    case 3:
                        Path p = fileHandler.createBackup();
                        System.out.println("Backup at: " + p.toAbsolutePath());
                        break;
                    case 4:
                        fileHandler.restoreLatestBackup();
                        System.out.println("Restored latest backup.");
                        break;
                    case 5:
                        return;
                    default:
                        break;
                }
            } catch (Exception e) {
                System.out.println("[ERROR] " + e.getMessage());
            }
            pause();
        }
    }

    private static void helpMenu() {
        System.out.println("\n=== Help & Documentation ===");
        System.out.println("- Submit New Request: Create a service request with category, priority, subject, description.");
        System.out.println("- View My Requests: Find your requests by email. Add follow-up comments.");
        System.out.println("- Administrator Panel: Requires PIN. Manage, assign, update and export requests.");
        System.out.println("- Generate Reports: Summary, breakdown by category/priority, average resolution time, export CSV.");
        System.out.println("- Data Management: Save/Load data, backups.");
        System.out.println("\nStatus Flow: OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED.");
        System.out.println("Categories include IT Support, Facilities, HR Services, General.");
        pause();
    }

    // ====== Sub-Flows ======
    private static void listRequestsForUser(User user) {
        List<ServiceRequest> list = requestManager.listByUserEmail(user.email);
        if (list.isEmpty()) {
            System.out.println("No requests found.");
            return;
        }
        sortSelectionMenu(list);
        printRequestTable(list);
        viewDetailsOption(list);
    }

    private static void searchRequestsForUser(User user) {
        String keyword = prompt("Keyword or Ticket ID");
        String kUpper = keyword.trim().toUpperCase(Locale.ROOT);
        if (kUpper.startsWith("REQ-")) {
            ServiceRequest r = requestManager.findById(kUpper);
            if (r != null && r.userEmail.equalsIgnoreCase(user.email)) {
                System.out.println(r.toDisplayString());
            } else {
                System.out.println("No matching requests.");
            }
            return;
        }
        List<ServiceRequest> list = requestManager.searchByKeyword(keyword).stream()
                .filter(r -> r.userEmail.equalsIgnoreCase(user.email))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            System.out.println("No matching requests.");
            return;
        }
        sortSelectionMenu(list);
        printRequestTable(list);
        viewDetailsOption(list);
    }

    private static void addCommentForUserFlow(User user) {
        String id = prompt("Enter Ticket ID").toUpperCase(Locale.ROOT).trim();
        ServiceRequest r = requestManager.findById(id);
        if (r == null) {
            System.out.println("Ticket not found.");
            return;
        }
        if (!r.userEmail.equalsIgnoreCase(user.email)) {
            System.out.println("You can only comment on your own requests.");
            return;
        }
        String c = prompt("Comment (single line)");
        if (c.trim().isEmpty()) {
            System.out.println("No comment entered. Cancelled.");
            return;
        }
        r.addComment(user.name + ": " + c);
        requestManager.markChanged();
        System.out.println("Comment added.");
        System.out.println(r.toDisplayString());
        pause();
    }

    private static void viewAssignedToAgent(User user) {
        if (!"AGENT".equalsIgnoreCase(user.role)) {
            System.out.println("You are not a Support Agent.");
            return;
        }
        List<ServiceRequest> list = requestManager.listByAssignedAgent(user.name);
        if (list.isEmpty()) {
            System.out.println("No requests assigned to you.");
            return;
        }
        sortSelectionMenu(list);
        printRequestTable(list);
        System.out.println("Select an action:");
        System.out.println("1. View Details");
        System.out.println("2. Update Status");
        System.out.println("3. Add Comment");
        System.out.println("4. Back");
        int a = readIntInRange(1, 4);
        switch (a) {
            case 1:
                viewDetailsOption(list);
                break;
            case 2: {
                String id = prompt("Ticket ID").toUpperCase(Locale.ROOT).trim();
                ServiceRequest r = requestManager.findById(id);
                if (r == null || r.assignedAgent == null || !user.name.equalsIgnoreCase(r.assignedAgent)) {
                    System.out.println("Ticket not found or not assigned to you.");
                    break;
                }
                String status = pickFromList("New Status", STATUSES);
                requestManager.updateStatus(r, status, user.name);
                if ("RESOLVED".equalsIgnoreCase(status)) {
                    String note = prompt("Resolution note");
                    r.resolutionNotes = note;
                    r.addComment("[RESOLVED] " + note);
                }
                System.out.println("Status updated.");
                break;
            }
            case 3: {
                String id = prompt("Ticket ID").toUpperCase(Locale.ROOT).trim();
                ServiceRequest r = requestManager.findById(id);
                if (r == null || r.assignedAgent == null || !user.name.equalsIgnoreCase(r.assignedAgent)) {
                    System.out.println("Ticket not found or not assigned to you.");
                    break;
                }
                String c = prompt("Comment");
                r.addComment(user.name + ": " + c);
                requestManager.markChanged();
                System.out.println("Comment added.");
                break;
            }
            case 4:
                break;
            default:
                break;
        }
    }

    private static void manageAllRequestsFlow() {
        while (true) {
            System.out.println("\n=== Manage Requests ===");
            System.out.println("1. View All");
            System.out.println("2. Filter by Status");
            System.out.println("3. Filter by Category");
            System.out.println("4. Filter by Priority");
            System.out.println("5. Filter by Date Range");
            System.out.println("6. Search by Keyword");
            System.out.println("7. View by User (email)");
            System.out.println("8. Back");
            System.out.print("Enter choice: ");
            int ch = readIntInRange(1, 8);
            List<ServiceRequest> list = new ArrayList<>();
            if (ch == 1) list = requestManager.listAll();
            else if (ch == 2) list = requestManager.filterByStatus(pickFromList("Status", STATUSES));
            else if (ch == 3) list = requestManager.filterByCategory(pickFromList("Category", CATEGORY_GROUPS));
            else if (ch == 4) list = requestManager.filterByPriority(pickFromList("Priority", PRIORITIES));
            else if (ch == 5) {
                System.out.println("Enter From date-time (yyyy-MM-dd HH:mm:ss) or blank for no lower bound:");
                String from = scanner.nextLine().trim();
                System.out.println("Enter To date-time (yyyy-MM-dd HH:mm:ss) or blank for no upper bound:");
                String to = scanner.nextLine().trim();
                LocalDateTime f = from.isEmpty() ? null : LocalDateTime.parse(from, DTF);
                LocalDateTime t = to.isEmpty() ? null : LocalDateTime.parse(to, DTF);
                list = requestManager.filterByDateRange(f, t);
            } else if (ch == 6) {
                String kw = prompt("Keyword");
                list = requestManager.searchByKeyword(kw);
            } else if (ch == 7) {
                String em = prompt("User Email");
                list = requestManager.listByUserEmail(em);
            } else if (ch == 8) return;

            if (list.isEmpty()) {
                System.out.println("No results.");
                continue;
            }
            sortSelectionMenu(list);
            printRequestTable(list);
            System.out.println("Select an action:");
            System.out.println("1. View Details");
            System.out.println("2. Update Status");
            System.out.println("3. Assign to Agent");
            System.out.println("4. Add Comment");
            System.out.println("5. Delete Request");
            System.out.println("6. Back");
            int a = readIntInRange(1, 6);
            switch (a) {
                case 1:
                    viewDetailsOption(list);
                    break;
                case 2:
                    updateRequestStatusFlow();
                    break;
                case 3:
                    assignRequestFlow();
                    break;
                case 4:
                    addCommentAdminFlow();
                    break;
                case 5:
                    deleteRequestFlow();
                    break;
                case 6:
                    break; // back
                default:
                    break;
            }
        }
    }

    private static void assignRequestFlow() {
        String id = prompt("Ticket ID").toUpperCase(Locale.ROOT).trim();
        ServiceRequest r = requestManager.findById(id);
        if (r == null) { System.out.println("Not found."); return; }
        String agentName = prompt("Assign to Agent (name)");
        r.assignedAgent = agentName;
        r.addComment("[ASSIGN] Assigned to " + agentName);
        requestManager.markChanged();
        System.out.println("Assigned.");
    }

    private static void updateRequestStatusFlow() {
        String id = prompt("Ticket ID").toUpperCase(Locale.ROOT).trim();
        ServiceRequest r = requestManager.findById(id);
        if (r == null) { System.out.println("Not found."); return; }
        String status = pickFromList("New Status", STATUSES);
        requestManager.updateStatus(r, status, "ADMIN");
        if ("RESOLVED".equals(status)) {
            String note = prompt("Resolution note");
            r.resolutionNotes = note;
            r.addComment("[RESOLVED] " + note);
        }
        System.out.println("Status updated.");
    }

    private static void addCommentAdminFlow() {
        String id = prompt("Ticket ID").toUpperCase(Locale.ROOT).trim();
        ServiceRequest r = requestManager.findById(id);
        if (r == null) { System.out.println("Not found."); return; }
        String c = prompt("Comment");
        r.addComment("Admin: " + c);
        requestManager.markChanged();
        System.out.println("Comment added.");
    }

    private static void deleteRequestFlow() {
        String id = prompt("Ticket ID").toUpperCase(Locale.ROOT).trim();
        boolean ok = requestManager.deleteRequest(id);
        System.out.println(ok ? "Deleted." : "Ticket not found.");
    }

    private static void createUserFlow() {
        System.out.println("\n=== Create User ===");
        String name = prompt("Name");
        String dept = prompt("Department");
        String role = pickFromList("Role", new String[]{"ADMIN", "AGENT", "USER"});
        String email = prompt("Email");
        String phone = prompt("Phone");
        User u = requestManager.createUser(name, dept, role, email, phone);
        System.out.println("User created with ID: " + u.userId);
    }

    private static void manageUsersMenu() {
        while (true) {
            System.out.println("\n=== Users ===");
            System.out.println("1. List Users");
            System.out.println("2. Delete User by Email");
            System.out.println("3. Back");
            int ch = readIntInRange(1, 3);
            switch (ch) {
                case 1 -> {
                    for (User u : requestManager.getUsers()) {
                        System.out.println(u);
                    }
                }
                case 2 -> {
                    String email = prompt("Email");
                    boolean ok = requestManager.deleteUserByEmail(email);
                    System.out.println(ok ? "Deleted." : "User not found or has requests.");
                }
                case 3 -> { return; }
            }
        }
    }

    private static void exportSingleRequestFlow() {
        String id = prompt("Ticket ID").toUpperCase(Locale.ROOT).trim();
        ServiceRequest r = requestManager.findById(id);
        if (r == null) { System.out.println("Not found."); return; }
        try {
            Path p = fileHandler.exportRequestDetails(r);
            System.out.println("Exported to: " + p.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("[ERROR] " + e.getMessage());
        }
    }

    private static void sortSelectionMenu(List<ServiceRequest> list) {
        System.out.println("Sort by: 1=Created, 2=Priority, 3=Status, 4=None");
        int s = readIntInRange(1, 4);
        switch (s) {
            case 1:
                list.sort(Comparator.comparing((ServiceRequest r) -> r.createdDate));
                break;
            case 2:
                list.sort(Comparator.comparingInt(ServiceRequestSystem::priorityRank));
                break;
            case 3:
                list.sort(Comparator.comparing((ServiceRequest r) -> r.status));
                break;
            case 4:
                break;
            default:
                break;
        }
    }

    private static int priorityRank(ServiceRequest r) {
        String p = r.priority == null ? "" : r.priority.toUpperCase(Locale.ROOT);
        switch (p) {
            case "CRITICAL": return 0;
            case "HIGH": return 1;
            case "MEDIUM": return 2;
            default: return 3;
        }
    }

    private static void printRequestTable(List<ServiceRequest> list) {
        System.out.printf("%-3s | %-8s | %-9s | %-8s | %-20s | %-19s | %s\n",
                "#", "Ticket", "Status", "Priority", "Category", "Created", "Subject");
        System.out.println(String.join("", Collections.nCopies(108, "-")));
        for (int i = 0; i < list.size(); i++) {
            ServiceRequest r = list.get(i);
            System.out.printf("%-3d | %-8s | %-9s | %-8s | %-20s | %-19s | %s\n",
                    i + 1, r.ticketId, r.status, r.priority,
                    truncate(r.category, 20), r.createdDate.format(DTF), truncate(r.subject, 40));
        }
    }

    private static void viewDetailsOption(List<ServiceRequest> list) {
        String sel = prompt("Enter Ticket ID or row number to view details (or blank to skip)");
        if (sel.trim().isEmpty()) return;
        ServiceRequest r = null;
        if (sel.trim().matches("\\d+")) {
            int idx = Integer.parseInt(sel.trim());
            if (idx >= 1 && idx <= list.size()) {
                r = list.get(idx - 1);
            }
        }
        if (r == null) {
            String id = sel.trim().toUpperCase(Locale.ROOT);
            r = requestManager.findById(id);
        }
        if (r == null) { System.out.println("Not found."); return; }
        System.out.println(r.toDisplayString());
    }

    // ====== Input Helpers ======
    private static String prompt(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine().trim();
    }

    private static String promptMultiline(String label) {
        System.out.println(label + ":");
        StringBuilder sb = new StringBuilder();
        while (true) {
            String line = scanner.nextLine();
            if (line.equals(".")) break;
            sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private static boolean yesNo() {
        String s = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
        return s.startsWith("y");
    }

    private static int readIntInRange(int min, int max) {
        while (true) {
            String s = scanner.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (v < min || v > max) throw new NumberFormatException();
                return v;
            } catch (NumberFormatException e) {
                System.out.print("Enter a number between " + min + " and " + max + ": ");
            }
        }
    }

    private static String pickFromList(String label, String[] options) {
        System.out.println(label + ":");
        for (int i = 0; i < options.length; i++) {
            System.out.printf("[%d] %s\n", i + 1, options[i]);
        }
        System.out.print("Enter choice (1-" + options.length + "): ");
        int idx = readIntInRange(1, options.length);
        return options[idx - 1];
    }

    private static void pause() {
        System.out.print("\nPress ENTER to continue...");
        scanner.nextLine();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static void seedSampleData() {
        System.out.println("Seeding sample data...");
        User admin = requestManager.createUser("Alice Admin", "IT", "ADMIN", "admin@example.com", "100-000");
        User agent = requestManager.createUser("Tom Wilson", "IT Support", "AGENT", "tom.wilson@example.com", "100-101");
        User user1 = requestManager.createUser("Sarah Connor", "Marketing", "USER", "sarah.connor@example.com", "100-201");
        User user2 = requestManager.createUser("John Smith", "Finance", "USER", "john.smith@example.com", "100-202");

        ServiceRequest r1 = requestManager.createRequest(user1, "IT Support - Software", "HIGH",
                "Laptop crashed", "Blue screen on startup, needs urgent fix");
        requestManager.updateStatus(r1, "IN_PROGRESS", agent.name);
        r1.assignedAgent = agent.name;
        r1.addComment(agent.name + ": Investigating BSOD.");

        ServiceRequest r2 = requestManager.createRequest(user2, "Facilities - Maintenance", "MEDIUM",
                "Air conditioner leaking", "Water dripping from AC unit in room 204");
        requestManager.updateStatus(r2, "OPEN", admin.name);

        ServiceRequest r3 = requestManager.createRequest(user1, "HR Services - Payroll", "LOW",
                "Payslip correction", "Incorrect tax calculation in June payslip");
        requestManager.updateStatus(r3, "RESOLVED", admin.name);
        r3.resolutionNotes = "Corrected payroll entry and reissued payslip";
        r3.addComment("Admin: " + r3.resolutionNotes);
    }

    // ====== Data Models ======
    static class ServiceRequest {
        String ticketId;
        String userName, userDept, userEmail, userPhone;
        String category, priority, subject, description;
        String status, assignedAgent;
        LocalDateTime createdDate, lastUpdated, resolvedDate;
        String resolutionNotes;
        List<String> comments = new ArrayList<>();

        ServiceRequest() {}

        ServiceRequest(String ticketId, User user, String category, String priority, String subject, String description) {
            this.ticketId = ticketId;
            this.userName = user.name;
            this.userDept = user.department;
            this.userEmail = user.email;
            this.userPhone = user.phone;
            this.category = category;
            this.priority = priority;
            this.subject = subject;
            this.description = description;
            this.status = "OPEN";
            this.createdDate = LocalDateTime.now();
            this.lastUpdated = this.createdDate;
            this.assignedAgent = "";
        }

        void addComment(String c) {
            if (c == null || c.trim().isEmpty()) return;
            String ts = LocalDateTime.now().format(DTF);
            comments.add("[" + ts + "] " + c);
            this.lastUpdated = LocalDateTime.now();
        }

        String toDisplayString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== Request Details ===\n");
            sb.append("Ticket ID: ").append(ticketId).append('\n');
            sb.append("Status: ").append(status).append('\n');
            sb.append("Created: ").append(createdDate.format(DTF)).append('\n');
            sb.append("Priority: ").append(priority).append('\n');
            sb.append('\n');
            sb.append("User: ").append(userName).append(" (" ).append(userDept).append(")\n");
            sb.append("Email: ").append(userEmail).append('\n');
            sb.append('\n');
            sb.append("Subject: ").append(subject).append('\n');
            sb.append("Category: ").append(category).append('\n');
            sb.append('\n');
            sb.append("Description:\n").append(description).append('\n');
            sb.append('\n');
            sb.append("Assignment: ").append(assignedAgent == null ? "" : assignedAgent).append('\n');
            sb.append("Last Update: ").append(lastUpdated == null ? "" : lastUpdated.format(DTF)).append('\n');
            if (resolvedDate != null) {
                sb.append("Resolved: ").append(resolvedDate.format(DTF)).append('\n');
            }
            if (resolutionNotes != null && !resolutionNotes.trim().isEmpty()) {
                sb.append("Resolution Notes: ").append(resolutionNotes).append('\n');
            }
            sb.append('\n');
            sb.append("Comments:\n");
            if (comments.isEmpty()) {
                sb.append("(None)\n");
            } else {
                for (String c : comments) sb.append("- ").append(c).append('\n');
            }
            return sb.toString();
        }
    }

    static class User {
        String userId, name, department, role; // ADMIN, AGENT, USER
        String email, phone;
        List<String> requestHistory = new ArrayList<>(); // ticket IDs

        @Override
        public String toString() {
            return String.format("%s | %s | %s | %s | %s | %s | Tickets: %d",
                    userId, name, department, role, email, phone, requestHistory.size());
        }
    }

    // ====== Core Logic ======
    static class RequestManager {
        private final List<ServiceRequest> requests = new ArrayList<>();
        private final Map<String, ServiceRequest> byId = new HashMap<>();
        private final Map<String, Integer> idNumeric = new HashMap<>();
        private int nextSeq = 1;

        private final List<User> users = new ArrayList<>();
        private boolean changed = false;

        void markChanged() { changed = true; }
        boolean hasChanges() { return changed; }
        void clearChanges() { changed = false; }

        // Accessors for external modules
        List<User> getUsers() { return users; }
        List<ServiceRequest> getRequests() { return requests; }

        String previewNextTicketId() { return String.format("REQ-%03d", nextSeq); }

        private void calibrateNextSeq() {
            int max = 0;
            for (String id : byId.keySet()) {
                try {
                    String num = id.replace("REQ-", "");
                    max = Math.max(max, Integer.parseInt(num));
                } catch (Exception ignored) {}
            }
            nextSeq = Math.max(nextSeq, max + 1);
        }

        ServiceRequest createRequest(User user, String category, String priority, String subject, String description) {
            String ticketId = String.format("REQ-%03d", nextSeq++);
            ServiceRequest r = new ServiceRequest(ticketId, user, category, priority, subject, description);
            requests.add(r);
            byId.put(ticketId, r);
            if (user != null) user.requestHistory.add(ticketId);
            changed = true;
            return r;
        }

        boolean deleteRequest(String ticketId) {
            ServiceRequest r = byId.remove(ticketId);
            if (r == null) return false;
            requests.remove(r);
            for (User u : users) u.requestHistory.remove(ticketId);
            changed = true;
            return true;
        }

        void updateStatus(ServiceRequest r, String status, String actor) {
            if (r == null) return;
            r.status = status;
            r.lastUpdated = LocalDateTime.now();
            r.addComment("[STATUS] -> " + status + (actor == null ? "" : (" by " + actor)));
            if ("RESOLVED".equals(status) || "CLOSED".equals(status)) {
                r.resolvedDate = LocalDateTime.now();
            }
            changed = true;
        }

        ServiceRequest findById(String ticketId) {
            return byId.get(ticketId);
        }

        List<ServiceRequest> listAll() { return new ArrayList<>(requests); }

        List<ServiceRequest> listByUserEmail(String email) {
            return requests.stream().filter(r -> r.userEmail.equalsIgnoreCase(email)).collect(Collectors.toList());
        }

        List<ServiceRequest> listByAssignedAgent(String agentName) {
            return requests.stream().filter(r -> agentName.equalsIgnoreCase(r.assignedAgent)).collect(Collectors.toList());
        }

        List<ServiceRequest> filterByStatus(String status) {
            return requests.stream().filter(r -> r.status.equalsIgnoreCase(status)).collect(Collectors.toList());
        }

        List<ServiceRequest> filterByCategory(String category) {
            return requests.stream().filter(r -> r.category.equalsIgnoreCase(category)).collect(Collectors.toList());
        }

        List<ServiceRequest> filterByPriority(String priority) {
            return requests.stream().filter(r -> r.priority.equalsIgnoreCase(priority)).collect(Collectors.toList());
        }

        List<ServiceRequest> filterByDateRange(LocalDateTime from, LocalDateTime to) {
            return requests.stream().filter(r -> {
                boolean ok = true;
                if (from != null) ok &= !r.createdDate.isBefore(from);
                if (to != null) ok &= !r.createdDate.isAfter(to);
                return ok;
            }).collect(Collectors.toList());
        }

        List<ServiceRequest> searchByKeyword(String keyword) {
            String kw = keyword.toLowerCase(Locale.ROOT);
            return requests.stream().filter(r ->
                    (r.subject != null && r.subject.toLowerCase(Locale.ROOT).contains(kw)) ||
                    (r.description != null && r.description.toLowerCase(Locale.ROOT).contains(kw))
            ).collect(Collectors.toList());
        }

        // ===== Users =====
        Optional<User> findUserByEmail(String email) {
            return users.stream().filter(u -> u.email.equalsIgnoreCase(email)).findFirst();
        }

        User findOrCreateUserByEmail(String email, String name, String dept, String role, String phone) {
            Optional<User> u = findUserByEmail(email);
            return u.orElseGet(() -> createUser(name, dept, role, email, phone));
        }

        User createUser(String name, String dept, String role, String email, String phone) {
            User u = new User();
            u.userId = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            u.name = name; u.department = dept; u.role = role; u.email = email; u.phone = phone;
            users.add(u);
            changed = true;
            return u;
        }

        boolean deleteUserByEmail(String email) {
            Optional<User> u = findUserByEmail(email);
            if (u.isEmpty()) return false;
            if (!u.get().requestHistory.isEmpty()) return false; // do not delete if linked
            users.remove(u.get());
            changed = true;
            return true;
        }

        // ===== Persistence support =====
        void replaceAll(List<User> users, List<ServiceRequest> requests) {
            this.users.clear();
            this.users.addAll(users);
            this.requests.clear();
            this.requests.addAll(requests);
            this.byId.clear();
            for (ServiceRequest r : requests) this.byId.put(r.ticketId, r);
            calibrateNextSeq();
            this.changed = false;
        }
    }

    // ====== Reporting ======
    static class ReportGenerator {
        private final RequestManager rm;
        ReportGenerator(RequestManager rm) { this.rm = rm; }

        void printSummaryStatistics() {
            List<ServiceRequest> all = rm.listAll();
            long total = all.size();
            long open = all.stream().filter(r -> r.status.equals("OPEN")).count();
            long inprog = all.stream().filter(r -> r.status.equals("IN_PROGRESS")).count();
            long resolved = all.stream().filter(r -> r.status.equals("RESOLVED")).count();
            long closed = all.stream().filter(r -> r.status.equals("CLOSED")).count();
            System.out.println("Total: " + total + ", Open: " + open + ", In Progress: " + inprog + ", Resolved: " + resolved + ", Closed: " + closed);
        }

        void printByCategory() {
            Map<String, Long> map = rm.listAll().stream().collect(Collectors.groupingBy(r -> r.category, Collectors.counting()));
            map.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                    System.out.printf("%-30s : %d%n", e.getKey(), e.getValue()));
        }

        void printByPriority() {
            Map<String, Long> map = rm.listAll().stream().collect(Collectors.groupingBy(r -> r.priority, Collectors.counting()));
            map.entrySet().stream().sorted((a, b) -> Integer.compare(rank(a.getKey()), rank(b.getKey())))
                    .forEach(e -> System.out.printf("%-8s : %d%n", e.getKey(), e.getValue()));
        }

        private int rank(String p) {
            String v = p == null ? "" : p.toUpperCase(Locale.ROOT);
            switch (v) {
                case "CRITICAL": return 0;
                case "HIGH": return 1;
                case "MEDIUM": return 2;
                default: return 3;
            }
        }

        void printAverageResolutionTime() {
            List<ServiceRequest> resolved = rm.listAll().stream().filter(r -> r.resolvedDate != null).collect(Collectors.toList());
            if (resolved.isEmpty()) { System.out.println("No resolved requests."); return; }
            double avgMinutes = resolved.stream()
                    .mapToLong(r -> Duration.between(r.createdDate, r.resolvedDate).toMinutes())
                    .average().orElse(0);
            System.out.printf("Average resolution time: %.1f minutes%n", avgMinutes);
        }
    }

    // ====== File I/O ======
    static class FileHandler {
        private final RequestManager rm;
        private final Path dataDir = Paths.get("data");
        private final Path exportDir = Paths.get("exports");
        private final Path requestsFile = dataDir.resolve("requests.txt");
        private final Path usersFile = dataDir.resolve("users.txt");

        FileHandler(RequestManager rm) { this.rm = rm; }

        void ensureDirs() throws IOException {
            if (!Files.exists(dataDir)) Files.createDirectories(dataDir);
            if (!Files.exists(exportDir)) Files.createDirectories(exportDir);
        }

        void saveData() throws IOException {
            ensureDirs();
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(usersFile, StandardCharsets.UTF_8))) {
                for (User u : rm.getUsers()) {
                    String line = String.join("|",
                            enc(u.userId), enc(u.name), enc(u.department), enc(u.role), enc(u.email), enc(u.phone),
                            enc(String.join(";;", u.requestHistory))
                    );
                    out.println(line);
                }
            }
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(requestsFile, StandardCharsets.UTF_8))) {
                for (ServiceRequest r : rm.getRequests()) {
                    String commentsJoined = r.comments.stream().map(FileHandler::enc).collect(Collectors.joining(";;"));
                    String line = String.join("|",
                            enc(r.ticketId), enc(r.userName), enc(r.userDept), enc(r.userEmail), enc(r.userPhone),
                            enc(r.category), enc(r.priority), enc(r.subject), enc(r.description), enc(r.status),
                            enc(nullToEmpty(r.assignedAgent)), enc(dt(r.createdDate)), enc(dt(r.lastUpdated)),
                            enc(dt(r.resolvedDate)), enc(nullToEmpty(r.resolutionNotes)), enc(commentsJoined)
                    );
                    out.println(line);
                }
            }
            rm.clearChanges();
        }

        void loadData() throws IOException {
            ensureDirs();
            List<User> users = new ArrayList<>();
            List<ServiceRequest> reqs = new ArrayList<>();
            if (Files.exists(usersFile)) {
                try (BufferedReader br = Files.newBufferedReader(usersFile, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] parts = split(line);
                        if (parts.length < 7) continue;
                        User u = new User();
                        u.userId = dec(parts[0]);
                        u.name = dec(parts[1]);
                        u.department = dec(parts[2]);
                        u.role = dec(parts[3]);
                        u.email = dec(parts[4]);
                        u.phone = dec(parts[5]);
                        String hist = dec(parts[6]);
                        if (!hist.trim().isEmpty()) Collections.addAll(u.requestHistory, hist.split(";;"));
                        users.add(u);
                    }
                }
            }
            if (Files.exists(requestsFile)) {
                try (BufferedReader br = Files.newBufferedReader(requestsFile, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] p = split(line);
                        if (p.length < 16) continue;
                        ServiceRequest r = new ServiceRequest();
                        r.ticketId = dec(p[0]);
                        r.userName = dec(p[1]);
                        r.userDept = dec(p[2]);
                        r.userEmail = dec(p[3]);
                        r.userPhone = dec(p[4]);
                        r.category = dec(p[5]);
                        r.priority = dec(p[6]);
                        r.subject = dec(p[7]);
                        r.description = dec(p[8]);
                        r.status = dec(p[9]);
                        r.assignedAgent = emptyToNull(dec(p[10]));
                        r.createdDate = parseDt(dec(p[11]));
                        r.lastUpdated = parseDt(dec(p[12]));
                        r.resolvedDate = parseDt(dec(p[13]));
                        r.resolutionNotes = emptyToNull(dec(p[14]));
                        String commentsJoined = dec(p[15]);
                        if (!commentsJoined.trim().isEmpty()) {
                            for (String c : commentsJoined.split(";;")) r.comments.add(dec(c));
                        }
                        reqs.add(r);
                    }
                }
            }
            rm.replaceAll(users, reqs);
        }

        Path exportAllRequestsCsv() throws IOException {
            ensureDirs();
            Path csv = exportDir.resolve("requests.csv");
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
                out.println("TicketId,Status,Priority,Category,Created,User,Department,Email,Subject,AssignedAgent");
                for (ServiceRequest r : rm.getRequests()) {
                    out.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                            csvEsc(r.ticketId), csvEsc(r.status), csvEsc(r.priority), csvEsc(r.category), csvEsc(dt(r.createdDate)),
                            csvEsc(r.userName), csvEsc(r.userDept), csvEsc(r.userEmail), csvEsc(r.subject), csvEsc(nullToEmpty(r.assignedAgent)));
                }
            }
            return csv;
        }

        Path exportRequestDetails(ServiceRequest r) throws IOException {
            ensureDirs();
            Path txt = exportDir.resolve(r.ticketId + ".txt");
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(txt, StandardCharsets.UTF_8))) {
                out.print(r.toDisplayString());
            }
            return txt;
        }

        Path createBackup() throws IOException {
            ensureDirs();
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path dir = dataDir.resolve("backup_" + ts);
            Files.createDirectories(dir);
            if (Files.exists(usersFile)) Files.copy(usersFile, dir.resolve("users.txt"));
            if (Files.exists(requestsFile)) Files.copy(requestsFile, dir.resolve("requests.txt"));
            return dir;
        }

        void restoreLatestBackup() throws IOException {
            ensureDirs();
            File[] backups = dataDir.toFile().listFiles((d, n) -> n.startsWith("backup_"));
            if (backups == null || backups.length == 0) throw new IOException("No backups found.");
            File latest = backups[0];
            for (File f : backups) if (f.getName().compareTo(latest.getName()) > 0) latest = f;
            Path dir = latest.toPath();
            Path u = dir.resolve("users.txt");
            Path r = dir.resolve("requests.txt");
            if (Files.exists(u)) Files.copy(u, usersFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (Files.exists(r)) Files.copy(r, requestsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            loadData();
        }

        private static String enc(String s) {
            if (s == null) s = "";
            return java.util.Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
        }

        private static String dec(String s) {
            try {
                return new String(java.util.Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                // fallback for plain text (older format)
                return s;
            }
        }

        private static String[] split(String line) {
            // tokens are base64, but separator is '|'
            return line.split("\\|", -1);
        }

        private static String dt(LocalDateTime t) { return t == null ? "" : t.format(DTF); }
        private static LocalDateTime parseDt(String s) { return (s == null || s.trim().isEmpty()) ? null : LocalDateTime.parse(s, DTF); }
        private static String nullToEmpty(String s) { return s == null ? "" : s; }
        private static String emptyToNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s; }
        private static String csvEsc(String s) {
            if (s == null) return "";
            String v = s.replace("\"", "\"\"");
            if (v.contains(",") || v.contains("\n") || v.contains("\"")) return "\"" + v + "\"";
            return v;
        }
    }
}
