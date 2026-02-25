package com.benktesh.fooddonation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

// MongoDB imports
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

public class FoodDonationSystem {

    // In-memory caches (also persisted in MongoDB)
    private static List<Donor> donors = new ArrayList<>();
    private static List<Recipient> recipients = new ArrayList<>();
    private static List<String> feedbackComments = new ArrayList<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FoodDonationSystem::createAndShowUI);
    }

    // ---------- UI SETUP ----------

    private static void createAndShowUI() {
        // Initialize DB and load existing data
        Database.init();
        donors = Database.loadDonors();
        recipients = Database.loadRecipients();
        feedbackComments = Database.loadFeedbackComments();

        JFrame frame = new JFrame("Food Donation System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        // Title
        JLabel title = new JLabel("Food Donation System", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        frame.add(title, BorderLayout.NORTH);

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new GridLayout(5, 2, 15, 15));
        buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
        buttonsPanel.setBackground(new Color(245, 245, 245));

        // Create all main menu buttons
        buttonsPanel.add(createMenuButton("Register as Donor", null, FoodDonationSystem::registerDonor));
        buttonsPanel.add(createMenuButton("Register as Recipient", null, FoodDonationSystem::registerRecipient));
        buttonsPanel.add(createMenuButton("Donate Food", null, FoodDonationSystem::donateFood));
        buttonsPanel.add(createMenuButton("Request Food", null, FoodDonationSystem::requestFood));
        buttonsPanel.add(createMenuButton("Display Donors & Recipients", null, FoodDonationSystem::displayInformation));
        buttonsPanel.add(createMenuButton("Edit Donors List", null, FoodDonationSystem::editDonorsList));
        buttonsPanel.add(createMenuButton("Edit Recipients List", null, FoodDonationSystem::editRecipientsList));
        buttonsPanel.add(createMenuButton("Help", null, FoodDonationSystem::displayHelp));
        buttonsPanel.add(createMenuButton("Give Feedback", null, FoodDonationSystem::giveFeedback));
        buttonsPanel.add(createMenuButton("Reviews", null, FoodDonationSystem::displayFeedback));

        frame.add(buttonsPanel, BorderLayout.CENTER);

        // Clean shutdown of MongoDB client
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Database.close();
            }
        });

        frame.setVisible(true);
    }

    // Create a styled menu button with optional icon and action
    private static JButton createMenuButton(String text, String iconPath, Runnable action) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.PLAIN, 16));
        button.setBackground(Color.WHITE);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        Icon icon = loadIcon(iconPath, 32, 32);
        if (icon != null) {
            button.setIcon(icon);
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
        }

        button.addActionListener(e -> action.run());
        return button;
    }

    // Try to load an icon from classpath or file system
    private static Icon loadIcon(String path, int width, int height) {
        if (path == null || path.isBlank()) return null;

        ImageIcon rawIcon = null;
        java.net.URL url = FoodDonationSystem.class.getResource(path);
        if (url != null) {
            rawIcon = new ImageIcon(url);
        } else {
            // fallback to direct file path
            rawIcon = new ImageIcon(path);
        }

        if (rawIcon.getIconWidth() <= 0) return null;

        Image img = rawIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }

    // Simple helper dialog methods
    private static void showInfo(String message) {
        JOptionPane.showMessageDialog(null, message, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ---------- BUSINESS LOGIC ----------

    static void registerDonor() {
        String name = askNonEmpty("Enter your name:");
        if (name == null) return;

        String address = askNonEmpty("Enter your address:");
        if (address == null) return;

        String mobile = askValidMobile();
        if (mobile == null) return;

        Donor donor = new Donor(name, address, mobile);
        donors.add(donor);
        Database.saveDonor(donor);

        showInfo("Thank you for registering as a donor!");
    }

    static void registerRecipient() {
        String name = askNonEmpty("Enter your name:");
        if (name == null) return;

        String address = askNonEmpty("Enter your address:");
        if (address == null) return;

        String mobile = askValidMobile();
        if (mobile == null) return;

        Recipient recipient = new Recipient(name, address, mobile);
        recipients.add(recipient);
        Database.saveRecipient(recipient);

        showInfo("Thank you for registering as a recipient!");
    }

    private static String askNonEmpty(String prompt) {
        while (true) {
            String value = JOptionPane.showInputDialog(prompt);
            if (value == null) return null; // cancel
            if (!value.isBlank()) return value.trim();
            showError("Input cannot be empty.");
        }
    }

    private static String askValidMobile() {
        while (true) {
            String value = JOptionPane.showInputDialog("Enter your 10-digit mobile number:");
            if (value == null) return null; // cancel
            if (isValidMobileNumber(value)) return value;
            showError("Please enter a valid 10-digit mobile number.");
        }
    }

    static boolean isValidMobileNumber(String mobileNumber) {
        return mobileNumber != null && mobileNumber.matches("\\d{10}");
    }

    static void donateFood() {
        if (donors.isEmpty()) {
            showInfo("No donors registered yet!");
            return;
        }
        if (recipients.isEmpty()) {
            showInfo("No recipients registered yet!");
            return;
        }

        Donor donor = chooseDonor("Choose donor:");
        if (donor == null) return;

        String foodItem = askNonEmpty("Enter food item:");
        if (foodItem == null) return;
        foodItem = foodItem.toLowerCase();

        Recipient requestingRecipient = findRequestingRecipient(foodItem);
        if (requestingRecipient != null) {
            donor.donate(foodItem);
            requestingRecipient.receiveDonation(foodItem);
            Database.saveDonor(donor);
            Database.saveRecipient(requestingRecipient);
            showInfo("Food donated successfully to " + requestingRecipient.getName() + "!");
        } else {
            donor.addToDonorItemList(foodItem);
            Database.saveDonor(donor);
            showInfo("Food item saved in donor's available list.");
        }
    }

    static Recipient findRequestingRecipient(String foodItem) {
        for (Recipient recipient : recipients) {
            if (recipient.getRequestedItems().contains(foodItem)) {
                return recipient;
            }
        }
        return null;
    }

    static void requestFood() {
        if (recipients.isEmpty()) {
            showInfo("No recipients registered yet!");
            return;
        }

        Recipient recipient = chooseRecipient("Choose recipient:");
        if (recipient == null) return;

        String foodRequest = askNonEmpty("Enter food request:");
        if (foodRequest == null) return;
        foodRequest = foodRequest.toLowerCase();

        if (recipient.getReceivedItems().contains(foodRequest)) {
            showInfo("Food already received by " + recipient.getName() + "!");
            return;
        }

        Donor matchingDonor = findMatchingDonor(foodRequest);
        if (matchingDonor != null) {
            recipient.receiveDonation(foodRequest);
            matchingDonor.deleteDonorItem(foodRequest);
            Database.saveRecipient(recipient);
            Database.saveDonor(matchingDonor);
            showInfo("Food received successfully from donor " + matchingDonor.getName() + "!");
        } else {
            recipient.request(foodRequest);
            Database.saveRecipient(recipient);
            showInfo("Food request saved!");
        }
    }

    static Donor findMatchingDonor(String foodRequest) {
        for (Donor donor : donors) {
            if (donor.getDonorItemList().contains(foodRequest)) {
                return donor;
            }
        }
        return null;
    }

    static void displayInformation() {
        StringBuilder sb = new StringBuilder();

        sb.append("DONORS:\n");
        for (Donor d : donors) {
            sb.append("Name: ").append(d.getName())
                    .append(", Address: ").append(d.getAddress())
                    .append(", Mobile: ").append(d.getMobileNumber()).append("\n");
            sb.append("  Donated Items: ").append(d.getDonatedItems()).append("\n");
            sb.append("  Available Items: ").append(d.getDonorItemList()).append("\n\n");
        }

        sb.append("\nRECIPIENTS:\n");
        for (Recipient r : recipients) {
            sb.append("Name: ").append(r.getName())
                    .append(", Address: ").append(r.getAddress())
                    .append(", Mobile: ").append(r.getMobileNumber()).append("\n");
            sb.append("  Requested Items: ").append(r.getRequestedItems()).append("\n");
            sb.append("  Received Items: ").append(r.getReceivedItems()).append("\n\n");
        }

        JOptionPane.showMessageDialog(null, new JScrollPane(new JTextArea(sb.toString(), 20, 60)),
                "Donors & Recipients", JOptionPane.INFORMATION_MESSAGE);
    }

    static void editDonorsList() {
        if (donors.isEmpty()) {
            showInfo("No donors registered yet!");
            return;
        }

        Donor donor = chooseDonor("Choose donor to edit:");
        if (donor == null) return;

        // Edit donor basic info
        String newName = JOptionPane.showInputDialog("Enter new name (leave blank to keep current):");
        if (newName != null && !newName.isBlank()) donor.setName(newName.trim());

        String newAddress = JOptionPane.showInputDialog("Enter new address (leave blank to keep current):");
        if (newAddress != null && !newAddress.isBlank()) donor.setAddress(newAddress.trim());

        String newMobile = JOptionPane.showInputDialog("Enter new mobile (leave blank to keep current):");
        if (newMobile != null && !newMobile.isBlank() && isValidMobileNumber(newMobile)) {
            donor.setMobileNumber(newMobile.trim());
        }

        // Edit donated items
        if (!donor.getDonatedItems().isEmpty()) {
            String[] donatedItemsArr = donor.getDonatedItems().toArray(new String[0]);
            String selected = (String) JOptionPane.showInputDialog(
                    null,
                    "Choose donated item to edit/delete:",
                    "Edit Donated Items",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    donatedItemsArr,
                    donatedItemsArr[0]
            );
            if (selected != null) {
                editStringList("donated item", donor.getDonatedItems(), selected);
            }
        }

        // Edit available items
        if (!donor.getDonorItemList().isEmpty()) {
            String[] availableArr = donor.getDonorItemList().toArray(new String[0]);
            String selected = (String) JOptionPane.showInputDialog(
                    null,
                    "Choose available item to edit/delete:",
                    "Edit Available Items",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    availableArr,
                    availableArr[0]
            );
            if (selected != null) {
                editStringList("available item", donor.getDonorItemList(), selected);
            }
        }

        Database.saveDonor(donor);
        showInfo("Donor information updated.");
    }

    static void editRecipientsList() {
        if (recipients.isEmpty()) {
            showInfo("No recipients registered yet!");
            return;
        }

        Recipient recipient = chooseRecipient("Choose recipient to edit:");
        if (recipient == null) return;

        // Edit basic info
        String newName = JOptionPane.showInputDialog("Enter new name (leave blank to keep current):");
        if (newName != null && !newName.isBlank()) recipient.setName(newName.trim());

        String newAddress = JOptionPane.showInputDialog("Enter new address (leave blank to keep current):");
        if (newAddress != null && !newAddress.isBlank()) recipient.setAddress(newAddress.trim());

        String newMobile = JOptionPane.showInputDialog("Enter new mobile (leave blank to keep current):");
        if (newMobile != null && !newMobile.isBlank() && isValidMobileNumber(newMobile)) {
            recipient.setMobileNumber(newMobile.trim());
        }

        // Edit requested items
        if (!recipient.getRequestedItems().isEmpty()) {
            String[] requestedArr = recipient.getRequestedItems().toArray(new String[0]);
            String selected = (String) JOptionPane.showInputDialog(
                    null,
                    "Choose requested item to edit/delete:",
                    "Edit Requested Items",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    requestedArr,
                    requestedArr[0]
            );
            if (selected != null) {
                editStringList("requested item", recipient.getRequestedItems(), selected);
            }
        }

        // Edit received items
        if (!recipient.getReceivedItems().isEmpty()) {
            String[] receivedArr = recipient.getReceivedItems().toArray(new String[0]);
            String selected = (String) JOptionPane.showInputDialog(
                    null,
                    "Choose received item to edit/delete:",
                    "Edit Received Items",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    receivedArr,
                    receivedArr[0]
            );
            if (selected != null) {
                editStringList("received item", recipient.getReceivedItems(), selected);
            }
        }

        Database.saveRecipient(recipient);
        showInfo("Recipient information updated.");
    }

    // Generic edit/delete flow for a list of strings
    private static void editStringList(String label, List<String> list, String selected) {
        String[] actions = {"Edit", "Delete", "Cancel"};
        String action = (String) JOptionPane.showInputDialog(
                null,
                "What do you want to do with this " + label + "?",
                "Edit " + label,
                JOptionPane.QUESTION_MESSAGE,
                null,
                actions,
                actions[0]
        );

        if (action == null || action.equals("Cancel")) return;

        if (action.equals("Edit")) {
            String newValue = JOptionPane.showInputDialog("Enter new value (leave blank to cancel):");
            if (newValue != null && !newValue.isBlank()) {
                list.remove(selected);
                list.add(newValue.trim());
                showInfo("Item updated.");
            }
        } else if (action.equals("Delete")) {
            list.remove(selected);
            showInfo("Item deleted.");
        }
    }

    static void displayHelp() {
        StringBuilder help = new StringBuilder();
        help.append("Welcome to the Food Donation System!\n\n")
                .append("1. Register as a donor or recipient.\n")
                .append("2. Donors can list available food or donate directly.\n")
                .append("3. Recipients can request food.\n")
                .append("4. When a match is found, the system links donor and recipient.\n\n")
                .append("Thank you for using this application!\n\n")
                .append("CONTACT US\n")
                .append("Benktesh Kumar      benkteshkumar7@gmail.com\n");

        JOptionPane.showMessageDialog(null, help.toString(), "Help", JOptionPane.INFORMATION_MESSAGE);
    }

    static void giveFeedback() {
        String feedback = askNonEmpty("Enter your feedback:");
        if (feedback == null) return;

        String name = askNonEmpty("Enter your name:");
        if (name == null) return;

        String comment = name + ": " + feedback;
        feedbackComments.add(comment);
        Database.saveFeedbackComment(comment);

        showInfo("Thank you for your feedback!");
    }

    static void displayFeedback() {
        if (feedbackComments.isEmpty()) {
            showInfo("No feedback available yet!");
            return;
        }

        StringBuilder sb = new StringBuilder("Feedback Comments:\n\n");
        for (String c : feedbackComments) {
            sb.append("- ").append(c).append("\n");
        }

        JOptionPane.showMessageDialog(null, new JScrollPane(new JTextArea(sb.toString(), 20, 50)),
                "Feedback", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- SELECTION HELPERS ----------

    private static Donor chooseDonor(String message) {
        if (donors.isEmpty()) return null;
        String[] names = donors.stream().map(Donor::getName).toArray(String[]::new);
        String selected = (String) JOptionPane.showInputDialog(
                null,
                message,
                "Select Donor",
                JOptionPane.QUESTION_MESSAGE,
                null,
                names,
                names[0]
        );
        if (selected == null) return null;
        for (Donor d : donors) {
            if (d.getName().equals(selected)) return d;
        }
        return null;
    }

    private static Recipient chooseRecipient(String message) {
        if (recipients.isEmpty()) return null;
        String[] names = recipients.stream().map(Recipient::getName).toArray(String[]::new);
        String selected = (String) JOptionPane.showInputDialog(
                null,
                message,
                "Select Recipient",
                JOptionPane.QUESTION_MESSAGE,
                null,
                names,
                names[0]
        );
        if (selected == null) return null;
        for (Recipient r : recipients) {
            if (r.getName().equals(selected)) return r;
        }
        return null;
    }

    // ---------- MODEL CLASSES ----------

    static class Donor {
        private String name;
        private String address;
        private String mobileNumber;
        private List<String> donatedItems;
        private List<String> donorItemList;

        public Donor(String name, String address, String mobileNumber) {
            this.name = name;
            this.address = address;
            this.mobileNumber = mobileNumber;
            this.donatedItems = new ArrayList<>();
            this.donorItemList = new ArrayList<>();
        }

        // Getters & setters
        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public String getAddress() { return address; }

        public void setAddress(String address) { this.address = address; }

        public String getMobileNumber() { return mobileNumber; }

        public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

        public List<String> getDonatedItems() { return donatedItems; }

        public List<String> getDonorItemList() { return donorItemList; }

        // Behavior
        public void donate(String foodItem) {
            donatedItems.add(foodItem);
        }

        public void addToDonorItemList(String foodItem) {
            donorItemList.add(foodItem);
        }

        public void deleteDonorItem(String item) {
            donorItemList.remove(item);
        }

        // MongoDB conversions
        public Document toDocument() {
            return new Document("name", name)
                    .append("address", address)
                    .append("mobile", mobileNumber)
                    .append("donatedItems", donatedItems)
                    .append("donorItemList", donorItemList);
        }

        public static Donor fromDocument(Document doc) {
            String name = doc.getString("name");
            String address = doc.getString("address");
            String mobile = doc.getString("mobile");
            Donor d = new Donor(name, address, mobile);

            List<String> donated = doc.getList("donatedItems", String.class);
            if (donated != null) d.donatedItems.addAll(donated);

            List<String> available = doc.getList("donorItemList", String.class);
            if (available != null) d.donorItemList.addAll(available);

            return d;
        }
    }

    static class Recipient {
        private String name;
        private String address;
        private String mobileNumber;
        private List<String> requestedItems;
        private List<String> receivedItems;

        public Recipient(String name, String address, String mobileNumber) {
            this.name = name;
            this.address = address;
            this.mobileNumber = mobileNumber;
            this.requestedItems = new ArrayList<>();
            this.receivedItems = new ArrayList<>();
        }

        // Getters & setters
        public String getName() { return name; }

        public void setName(String name) { this.name = name; }

        public String getAddress() { return address; }

        public void setAddress(String address) { this.address = address; }

        public String getMobileNumber() { return mobileNumber; }

        public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

        public List<String> getRequestedItems() { return requestedItems; }

        public List<String> getReceivedItems() { return receivedItems; }

        // Behavior
        public void request(String foodRequest) {
            requestedItems.add(foodRequest);
        }

        public void receiveDonation(String foodItem) {
            receivedItems.add(foodItem);
        }

        // MongoDB conversions
        public Document toDocument() {
            return new Document("name", name)
                    .append("address", address)
                    .append("mobile", mobileNumber)
                    .append("requestedItems", requestedItems)
                    .append("receivedItems", receivedItems);
        }

        public static Recipient fromDocument(Document doc) {
            String name = doc.getString("name");
            String address = doc.getString("address");
            String mobile = doc.getString("mobile");
            Recipient r = new Recipient(name, address, mobile);

            List<String> requested = doc.getList("requestedItems", String.class);
            if (requested != null) r.requestedItems.addAll(requested);

            List<String> received = doc.getList("receivedItems", String.class);
            if (received != null) r.receivedItems.addAll(received);

            return r;
        }
    }

    // ---------- DATABASE HELPER (MongoDB Atlas) ----------

    static class Database {
        // TODO: you will put your real Atlas connection string here
        private static final String CONNECTION_STRING =
                "mongodb+srv://<db_user>:<db_password>7@fooddonation.nuxbbb8.mongodb.net/?retryWrites=true&w=majority&appName=FoodDonation&authSource=admin";
        private static final String DATABASE_NAME = "FoodDonation";

        private static MongoClient client;
        private static MongoDatabase db;

        public static void init() {
            if (client != null) return;
            try {
                client = MongoClients.create(CONNECTION_STRING);
                db = client.getDatabase(DATABASE_NAME);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        null,
                        "Could not connect to MongoDB Atlas.\nApp will run in offline mode.\n\n" + e.getMessage(),
                        "Database Error",
                        JOptionPane.ERROR_MESSAGE
                );
                client = null;
                db = null;
            }
        }

        public static void close() {
            if (client != null) {
                client.close();
                client = null;
                db = null;
            }
        }

        private static boolean isDbAvailable() {
            if (db == null) {
                init();
            }
            return db != null;
        }

        private static MongoCollection<Document> donorsCol() {
            return db.getCollection("donors");
        }

        private static MongoCollection<Document> recipientsCol() {
            return db.getCollection("recipients");
        }

        private static MongoCollection<Document> feedbackCol() {
            return db.getCollection("feedback");
        }

        // ---- Donors ----
        public static List<Donor> loadDonors() {
            List<Donor> list = new ArrayList<>();
            if (!isDbAvailable()) return list;
            try {
                for (Document doc : donorsCol().find()) {
                    list.add(Donor.fromDocument(doc));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return list;
        }

        public static void saveDonor(Donor d) {
            if (!isDbAvailable()) return;
            try {
                donorsCol().replaceOne(
                        Filters.eq("mobile", d.getMobileNumber()),
                        d.toDocument(),
                        new ReplaceOptions().upsert(true)
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ---- Recipients ----
        public static List<Recipient> loadRecipients() {
            List<Recipient> list = new ArrayList<>();
            if (!isDbAvailable()) return list;
            try {
                for (Document doc : recipientsCol().find()) {
                    list.add(Recipient.fromDocument(doc));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return list;
        }

        public static void saveRecipient(Recipient r) {
            if (!isDbAvailable()) return;
            try {
                recipientsCol().replaceOne(
                        Filters.eq("mobile", r.getMobileNumber()),
                        r.toDocument(),
                        new ReplaceOptions().upsert(true)
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ---- Feedback ----
        public static List<String> loadFeedbackComments() {
            List<String> list = new ArrayList<>();
            if (!isDbAvailable()) return list;
            try {
                for (Document doc : feedbackCol().find()) {
                    String c = doc.getString("comment");
                    if (c != null) list.add(c);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return list;
        }

        public static void saveFeedbackComment(String comment) {
            if (!isDbAvailable()) return;
            try {
                feedbackCol().insertOne(new Document("comment", comment));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

