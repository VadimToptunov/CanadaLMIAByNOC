package nocservice;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;

@SpringBootApplication(exclude = {R2dbcAutoConfiguration.class})
public class AppMain implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(AppMain.class, args);
    }

    @Override
    public void run(String... args) {
        AppBody app = new AppBody();
        app.createAFileByNoc();
    }
}
