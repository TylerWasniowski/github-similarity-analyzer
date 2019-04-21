import java.io.IOException;
import java.net.URL;
import java.util.Scanner;

public class Helper {
    public static String makeRequest(String path) throws IOException {
        URL url = new URL(
                "https://api.github.com" + path + "?" +
                "client_id=" + Config.GITHUB_CLIENT_ID + "&" +
                "client_secret=" + Config.GITHUB_CLIENT_SECRET
        );

        StringBuilder response = new StringBuilder();
        try (Scanner reposListScanner = new Scanner(url.openStream())) {
            while (reposListScanner.hasNextLine()) {
                response.append(reposListScanner.nextLine());
            }
        }

        return response.toString();
    }
}
