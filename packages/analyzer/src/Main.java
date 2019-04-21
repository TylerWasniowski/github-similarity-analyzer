import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {
    private static Logger logger;

    static {
        try {
            logger = new Logger("log.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        logger.log("Started with arguments: " + Arrays.toString(args));
        if (args.length < 1) {
            logger.log("No arguments provided. Exiting...");
            logger.flushLog();
            logger.close();
            return;
        }

        StringBuilder job = new StringBuilder();
        try (BufferedReader jobReader = new BufferedReader(new FileReader(args[0]))) {
            jobReader.lines().forEach(job::append);
        } catch (IOException e) {
            e.printStackTrace();
            logger.log(e.getMessage());
            logger.flushLog();
            logger.close();
            return;
        }
        logger.log("Job file read: " + job.toString());
        logger.flushLog();
        JSONObject jobObject = new JSONObject(job.toString());


        ExecutorService service = Executors.newFixedThreadPool(8);
        Set<String> repos = new HashSet<>();

        // Get repos from users
        List<Future<List<String>>> repoFutures = new ArrayList<>();
        JSONArray userObjects = (JSONArray) jobObject.get("users");
        for (Object userObject : userObjects) {
            String user = (String) userObject;
            logger.log("User: " + user);
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


        // Get files from repos
        Map<String, List<String>> repoToFiles = new HashMap<>();
        repos
            .parallelStream()
            .forEach((repo) -> repoToFiles.put(repo, getFiles(repo)));


        logger.log("Repos: " + repos);
        logger.log("Files: " + repoToFiles);
        logger.flushLog();
        logger.close();
    }

    private static List<String> getRepos(String user) {
        List<String> repos = new ArrayList<>();

        try {
            String reposString = Helper.makeRequest("/users/" + user + "/repos?per_page=100");
            JSONArray reposArray = new JSONArray(reposString);

            for (Object repoObject : reposArray) {
                repos.add((String) ((JSONObject) repoObject).get("full_name"));
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.log(e.getMessage());
            logger.flushLog();
        }

        return repos;
    }

    private static List<String> getFiles(String repo) {
        return getFilesRecurse(repo, "");
    }

    private static List<String> getFilesRecurse(String repo, String path) {
        List<String> files = new ArrayList<>();

        try {
            String modulesString = Helper.makeRequest("/repos/" + repo + "/contents" + path);

            JSONArray filesArray = new JSONArray(modulesString);
            for (Object moduleObject : filesArray) {
                JSONObject moduleJson = (JSONObject) moduleObject;
                if (moduleJson.get("type").equals("file")) {
                    files.add((String) moduleJson.get("url"));
                } else {
                    files.addAll(getFilesRecurse(repo, "/" + moduleJson.get("path")));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.log(e.getMessage());
            logger.flushLog();
        }

        return files;
    }
}
