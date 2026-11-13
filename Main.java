package mydb;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Консольная СУБД запущена ===");

        Database manager = new Database();
        manager.loadFromDisk();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("SQL> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                manager.saveToDisk();
                System.out.println("Работа завершена. Данные сохранены.");
                break;
            }
            if (input.isEmpty()) continue;

            manager.executeQuery(input);
        }
        scanner.close();
    }
}
