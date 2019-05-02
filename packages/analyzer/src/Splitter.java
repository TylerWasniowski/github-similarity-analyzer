import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Splitter {
    private Logger logger;
    private long partSizeInBytes;
    private int threads;

    public Splitter(Logger logger) {
        this.logger = logger;
        this.partSizeInBytes = Config.DEFAULT_PART_SIZE_IN_BYTES;
        this.threads = Config.DEFAULT_SPLITTER_THREADS;
    }

    public Splitter(Logger logger, int threads) {
        this(logger);
        this.threads = threads;
    }

    public Splitter(Logger logger, int threads, long partSizeInBytes) {
        this(logger, threads);
        this.partSizeInBytes = partSizeInBytes;
    }

    public void makeParts(String jobName) {
        makeParts(jobName, (partName) -> logger.log("Finished making part " + partName));
    }

    public void makeParts(String jobName, Consumer<String> processPart) {
        // Parse job file
        StringBuilder job = new StringBuilder();
        try (BufferedReader jobReader = new BufferedReader(new FileReader(jobName))) {
            jobReader.lines().forEach(job::append);
        } catch (IOException e) {
            e.printStackTrace();
            logger.log("Job file not found: " + jobName);
            logger.close();
            return;
        }
        logger.log("Job file read: " + job.toString());
        logger.flushLog();
        JSONObject jobObject = new JSONObject(job.toString());


        ExecutorService service = Executors.newFixedThreadPool(threads);
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
                logger.log("Failure to resolve repos for some user");
            }
        }


        // Get files from repos
        ConcurrentHashMap<String, List<Pair<String, Integer>>> repoToFiles = new ConcurrentHashMap<>();
        repos
                .parallelStream()
                .map((repo) -> service.submit(() -> repoToFiles.put(repo, getFiles(repo))))
                .forEach((future) -> {
                    try {
                        future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                        logger.log("Failure to resolve files for some repo");
                    }
                });
        logger.flushLog();


        // Make parts
        List<Pair<ConcurrentHashMap<String, List<Pair<String, Integer>>>, Integer>> parts = new ArrayList<>();
        parts.add(new Pair<>(new ConcurrentHashMap<>(), 0));
        for (String repo : repoToFiles.keySet()) {
            List<Pair<String, Integer>> files = repoToFiles.get(repo);
            for (Pair<String, Integer> fileToSize : files) {
                Pair<ConcurrentHashMap<String, List<Pair<String, Integer>>>, Integer> partToSize = parts.get(parts.size() - 1);
                if (partToSize.getSecond() + fileToSize.getSecond() > partSizeInBytes) {
                    partToSize = new Pair<>(new ConcurrentHashMap<>(), 0);
                    parts.add(partToSize);
                }

                partToSize.getFirst().computeIfAbsent(repo, (r) -> new ArrayList<>());
                partToSize.getFirst().get(repo).add(fileToSize);
                partToSize.setSecond(partToSize.getSecond() + fileToSize.getSecond());
            }
        }

        // Serialize parts
        AtomicInteger partNumber = new AtomicInteger(0);
        parts
                .parallelStream()
                .map((partToSize) -> service.submit(() -> {
                            String namePrefix = jobName.contains(".") ?
                                    jobName.substring(0, jobName.indexOf('.')) :
                                    jobName;

                            String partName = namePrefix + partNumber.getAndIncrement() + ".part";

                            try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(partName))) {
                                outputStream.writeObject(partToSize);
                                outputStream.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                                logger.log("Error writing part: part " + partNumber + " could not be written.");
                                logger.close();
                            }

                            logger.log("Finished writing part " + partName + " to file");
                            logger.log("Part " + partName + ": " + partToSize);
                            processPart.accept(partName);
                        })
                ).forEach((future) -> {
                    try {
                        future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                        logger.log("Failure to write some part to file");
                    }
                });
        service.shutdown();
        logger.log("Finished writing and processing parts");

        logger.close();
    }

    private List<String> getRepos(String user) {
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

    private List<Pair<String, Integer>> getFiles(String repo) {
        return getFilesRecurse(repo, "");
    }

    private List<Pair<String, Integer>> getFilesRecurse(String repo, String path) {
        List<Pair<String, Integer>> files = new ArrayList<>();

        try {
            String modulesString = Helper.makeRequest("/repos/" + repo + "/contents" + path);

            JSONArray filesArray = new JSONArray(modulesString);
            for (Object moduleObject : filesArray) {
                JSONObject moduleJson = (JSONObject) moduleObject;
                if (moduleJson.get("type").equals("file")) {
                    files.add(new Pair<>((String) moduleJson.get("path"), (Integer) moduleJson.get("size")));
                } else if (moduleJson.get("type").equals("dir")) {
                    files.addAll(getFilesRecurse(repo, "/" + moduleJson.get("path")));
                } else {
                    logger.log("Found symlink");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.log(e.getMessage());
            logger.flushLog();
        }

        return files;
    }


    public static void main(String[] args) {
        Logger logger;
        try {
            logger = new Logger("splitter_log.txt");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        int threads = Config.DEFAULT_SPLITTER_THREADS;
        //noinspection Duplicates
        if (args.length >= 2) {
            try {
                threads = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                printUsage();
                logger.log("Error parsing threads");
                logger.close();
                return;
            }
        }

        long partSizeInBytes = Config.DEFAULT_PART_SIZE_IN_BYTES;
        if (args.length == 3) {
            try {
                partSizeInBytes = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                printUsage();
                logger.log("Error parsing part_size_in_bytes");
                logger.close();
                return;
            }
        }

        Splitter splitter = new Splitter(logger, threads, partSizeInBytes);
        splitter.makeParts(args[0]);
    }

    private static void printUsage() {
        System.out.println("Usage: <job_filepath> [threads] [part_size_in_bytes]");
    }
}
