import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {
    private static BufferedWriter logWriter;

    static {
        try {
            logWriter = new BufferedWriter(new FileWriter("log.txt", true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        log("Started with arguments: " + Arrays.toString(args));
        if (args.length < 1) {
            log("No arguments provided. Exiting...");
            flushLog();
            close();
            return;
        }

        StringBuilder job = new StringBuilder();
        try (BufferedReader jobReader = new BufferedReader(new FileReader(args[0]))) {
            jobReader.lines().forEach(job::append);
        } catch (IOException e) {
            e.printStackTrace();
            log(e.getMessage());
            close();
            return;
        }
        log("Job file read: " + job.toString());
        flushLog();
        JSONObject jobObject = new JSONObject(job.toString());

        Set<String> repos = new HashSet<>();

        // Get repos from user
        List<Future<List<String>>> repoFutures = new ArrayList<>();
        JSONArray userObjects = (JSONArray) jobObject.get("users");
        ExecutorService service = Executors.newFixedThreadPool(8);
        for (Object userObject : userObjects) {
            log("User object: " + userObject);
            flushLog();
            String user = (String) userObject;
            log("User: " + user);
            flushLog();
            repoFutures.add(service.submit(() -> getRepos(user)));
        }

        JSONArray repoObjects = (JSONArray) jobObject.get("repos");
        for (Object repoObject : repoObjects) {
            repos.add((String) repoObject);
        }

        for (Future<List<String>> repoFuture : repoFutures) {
            try {
                repos.addAll(repoFuture.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        service.shutdown();

        log("Repos: " + repos.toString());
        flushLog();
        close();
    }

    private static List<String> getRepos(String user) {
        List<String> repos = new ArrayList<>();

        try {
            URL url = new URL("https://api.github.com/users/" + user + "/repos");
            InputStream reposListStream = url.openStream();

            StringBuilder reposFile = new StringBuilder();
            try (Scanner reposListScanner = new Scanner(reposListStream)) {
                while (reposListScanner.hasNextLine()) {
                    reposFile.append(reposListScanner.nextLine());
                }
            }

            JSONArray reposArray = new JSONArray(reposFile.toString());

            for (Object repoObject : reposArray) {
                repos.add((String) ((JSONObject) repoObject).get("full_name"));
            }
        } catch (IOException e) {
            e.printStackTrace();
            log(e.getMessage());
            flushLog();
        }

        return repos;
    }

    public static void log(String string) {
        try {
            logWriter.write(new Timestamp(System.currentTimeMillis()) + ": " + string + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void flushLog() {
        try {
            logWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void close() {
        try {
            logWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
