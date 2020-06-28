import java.util.Scanner;

public class AppMain {
    public static void main(String[] args) {
        System.out.println("Enter your NOC code: \n");
        Scanner scanner = new Scanner(System.in);
        new AppBody().createAFileByNoc(scanner.nextLine());
    }
}
