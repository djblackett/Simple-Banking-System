package banking;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.*;

public class Main {
    BankAccount currentlyLoggedIn = null;
    BankAccount transferRecipient = null;
    String url = "jdbc:sqlite:";
    //String url = "jdbc:sqlite:c\\sqlite\\db\\";

    private static final Map<String, BankAccount> accounts = new ConcurrentHashMap<>(100);
    static Scanner sc = new Scanner((System.in));

    public static void main(String[] args) {
        Main bank = new Main();

        String fileName = null;
        if (args[0].equals("-fileName")) {
            fileName = args[1];
        }

        bank.url += fileName;
        System.out.println(bank.url);
        boolean isLoggedIn = false;


        //connect to db
        bank.createDatabase();
        bank.createTable();




        while (true) {

            if (!isLoggedIn) {
                System.out.println("1. Create an account\n" +
                        "2. Log into account\n" +
                        "0. Exit");
                int userChoice = sc.nextInt();
                sc.nextLine();

                if (userChoice == 1) {
                    //create account
                    bank.createAccount();
                } else if (userChoice == 2) {
                    //login to account
                    System.out.println("Enter your card number: ");
                    String enteredCardNumber = sc.nextLine();
                    System.out.println("Entered card number: " + enteredCardNumber);
                    System.out.println("Enter your PIN: ");
                    String enteredPin = sc.nextLine();
                    System.out.println("Entered pin number: " + enteredPin);
                    if (enteredCardNumber.equals("") && enteredPin.length() > 4) {
                        enteredCardNumber = enteredPin;
                        enteredPin = sc.nextLine();
                    }

                    //todo null pointer exception
                    try {
                    BankAccount temp = bank.createCurrentlyLoggedInObj(enteredCardNumber);
                    if (temp.getPin().equals(enteredPin)) {
                        isLoggedIn = true;
                        System.out.println("You have successfully logged in!");
                        bank.currentlyLoggedIn = bank.createCurrentlyLoggedInObj(enteredCardNumber);
                    } else {
                        System.out.println("Wrong card or PIN!");
                    }
                    } catch (NullPointerException e) {
                        //e.printStackTrace();
                        System.out.println("Wrong card or PIN!");
                    }
                } else if (userChoice == 0) {
                    System.exit(0);
                }
            } else {

                // create object from SQL query
                // set to null when logout is chosen


                System.out.println("1. Balance\n" +
                        "2. Add income\n" +
                        "3. Do transfer\n" +
                        "4. Close account\n" +
                        "5. Log out\n" +
                        "0. Exit\n"
                        );
                int userChoice = sc.nextInt();
                sc.nextLine();
                if (userChoice == 1) {
                    //balance
                    int balance = bank.currentlyLoggedIn.getBalance();
                    System.out.println("Balance: " + balance);
                } else if (userChoice == 2) {
                    // add income
                    System.out.println("Enter income: ");
                    bank.addIncome(bank.currentlyLoggedIn.cardNumber, sc.nextInt());
                    System.out.println("Income was added!");

                } else if (userChoice == 3) {
                    //do transfer

                    bank.transfer();
                } else if (userChoice == 4) {
                    // close account
                    bank.closeAccount();
                    System.out.println("The account has been closed!");
                    isLoggedIn = false;
                    bank.currentlyLoggedIn = null;

                } else if (userChoice == 5) {
                    //logout
                    isLoggedIn = false;
                    bank.currentlyLoggedIn = null;
                    System.out.println("You have successfully logged out!");
                } else if (userChoice == 0) {
                    System.out.println("Bye!");
                    System.exit(0);
                }
            }
        }
    }

    private void closeAccount() {
        String sql = "DELETE FROM card WHERE number = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // set the corresponding param
            pstmt.setString(1, currentlyLoggedIn.cardNumber);
            // update
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        this.currentlyLoggedIn = null;
    }

    private BankAccount createCurrentlyLoggedInObj(String cardNumber) {
        String sql = "SELECT * FROM card WHERE number = ?";
        BankAccount loggedInUser = null;
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // set the corresponding param
            pstmt.setString(1, cardNumber);
            // update
            ResultSet rs = pstmt.executeQuery();

                String pin = rs.getString("pin");
                int balance = rs.getInt("balance");
                loggedInUser = new BankAccount(cardNumber, pin, balance);



        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return loggedInUser;
    }

    private void createAccount() {
        String cardNumberString;
        while (true) {
            StringBuilder cardNumber = new StringBuilder("400000");
            int[] numbers = new Random().ints(0, 10).limit(9).toArray();
            for (int i : numbers) {
                cardNumber.append(i);
            }

            int[] ints = new int[15];
            for (int i = 0; i < 15; i++) {
                ints[i] = Integer.parseInt(String.valueOf(cardNumber.toString().charAt(i)));
            }

            for (int i = 1; i <= 15; i++) {
                if (i % 2 == 1) {
                    ints[i - 1] *= 2;
                }
            }

            for (int i = 0; i < 15; i++) {
                if (ints[i] > 9) {
                    ints[i] -= 9;
                }
            }

            int controlNumber = 0;
            for (int i = 0; i < 15; i++) {
                controlNumber += ints[i];
            }

            int remainder = controlNumber % 10;
            int checkSum = 0;
            if (remainder != 0) {
                checkSum = 10 - remainder;
            }

            System.out.println(cardNumber.toString());
            cardNumber.append(checkSum);

            //cardNumber.append(3);
            cardNumberString = cardNumber.toString();
            if (!accounts.containsKey(cardNumberString)) {
                break;
            }
        }
        System.out.println(cardNumberString);
        int[] pinNumbers = new Random().ints(0, 10).limit(4).toArray();
        String pin = "";
        for (int i : pinNumbers) {
            pin += i;
        }
        System.out.println(pin);
        //accounts.put(cardNumberString, new BankAccount(cardNumberString, pin));
        this.insert(cardNumberString, pin);
    }

    private void transfer() {
        // todo error handling
        System.out.println("Enter recipient card number: ");
        String recipientCard = sc.nextLine();
        BankAccount transferFrom = currentlyLoggedIn;
        BankAccount transferTo = null;

        if (!passesLuhn(recipientCard)) {
            System.out.println("Probably you made mistake in the card number. Please try again!");
            return;
        }

        try {
            transferTo = createCurrentlyLoggedInObj(recipientCard);
            if (transferTo == null) {
                System.out.println("Such a card does not exist");
                return;
            }
        } catch (NullPointerException ignored) {

        }

        if (currentlyLoggedIn.cardNumber.equals(recipientCard)) {
            System.out.println("You can't transfer money to the same account!");
            return;
        }

        System.out.println("Enter amount: ");
        int amount = sc.nextInt();
        sc.nextLine();


        if (transferFrom.getBalance() < amount) {
            System.out.println("Not enough money!");
        } else if (transferFrom.getBalance() >= amount) {
            this.addIncome(transferFrom.cardNumber, (amount * (-1)));
            this.addIncome(transferTo.cardNumber, amount);
        }
    }

    boolean passesLuhn(String cardNumber) {

        int[] ints = new int[16];
        for (int i = 0; i < 15; i++) {
            ints[i] = Integer.parseInt(String.valueOf(cardNumber.charAt(i)));
        }

        for (int i = 1; i <= 15; i++) {
            if (i % 2 == 1) {
                ints[i - 1] *= 2;
            }
        }

        for (int i = 0; i < 15; i++) {
            if (ints[i] > 9) {
                ints[i] -= 9;
            }
        }

        int controlNumber = 0;
        for (int i = 0; i < 15; i++) {
            controlNumber += ints[i];
        }

        int remainder = controlNumber % 10;
        int checkSum = 0;
        if (remainder != 0) {
            checkSum = 10 - remainder;
        }
        return checkSum == Integer.parseInt(String.valueOf(cardNumber.charAt(15)));
    }


    static class BankAccount {
        private String cardNumber;
        private String pin;
        private int balance = 0;

        public BankAccount(String cardNumber, String pin) {
            this.cardNumber = cardNumber;
            this.pin = pin;
            balance = 0;
        }

        public BankAccount(String cardNumber, String pin, int balance) {
            this.cardNumber = cardNumber;
            this.pin = pin;
            this.balance = balance;
        }

        public String getPin() {
            return this.pin;
        }

        public int getBalance() {
            return balance;
        }
    }


    private void addIncome(String number, int amount) {
        BankAccount account = createCurrentlyLoggedInObj(number);
        int balance = account.getBalance();
        int newBalance = balance + amount;

        System.out.println("Balance: " + balance);
        System.out.println("Amount: " + amount);
        System.out.println("New balance: " + newBalance);

        String sql = "UPDATE card SET balance = ? WHERE number = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // set the corresponding param
            pstmt.setInt(1, newBalance);
            pstmt.setString(2, number);
            // update
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        this.currentlyLoggedIn.balance = newBalance;
    }
    public void insert(String cardNumber, String PIN) {
        String sql = "INSERT INTO card (number, pin, balance) VALUES(?, ?, 0)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // set the corresponding param
            pstmt.setString(1, cardNumber);
            pstmt.setString(2, PIN);
            // update
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private void createDatabase() {

        try (Connection conn = DriverManager.getConnection(this.url)) {
            if (conn != null) {
                DatabaseMetaData meta = conn.getMetaData();
                System.out.println("The driver name is " + meta.getDriverName());
                System.out.println("A new database has been created.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

     Connection connect() {
        Connection conn = null;
        try {
           conn = DriverManager.getConnection(this.url);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return conn;
    }

    void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS card (\n" +
                "id INTEGER,\n" +
                "number TEXT,\n" +
                "pin TEXT, \n" +
                "balance INTEGER\n" +
                ");";

        try (Connection conn = DriverManager.getConnection(this.url);
             Statement stmt = conn.createStatement()) {
            // create a new table
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}

