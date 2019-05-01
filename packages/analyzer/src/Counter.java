import org.json.JSONObject;

import java.io.*;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Counter {
    private Logger logger;
    private int threads;


    public Counter(Logger logger) {
        this.logger = logger;
        this.threads = Config.DEFAULT_COUNTER_THREADS;
    }

    public Counter(Logger logger, int threads) {
        this(logger);
        this.threads = threads;
    }

    public void countLines(String partName) {
        countLines(partName, (resultName) -> logger.log("Finished processing: " + resultName));
    }

    public void countLines(String partName, Consumer<String> processResult) {
        logger.log("Counting lines in " + partName);

        Pair<ConcurrentHashMap<String, List<Pair<String, Integer>>>, Integer> partToSize;
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(partName))) {
            //noinspection unchecked
            partToSize = (Pair<ConcurrentHashMap<String, List<Pair<String, Integer>>>, Integer>) inputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            logger.log("Error reading part: part " + partName + " could not be read.");
            logger.close();
            return;
        }

        ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> repoToCount = new ConcurrentHashMap<>();
        ExecutorService workerService = Executors.newFixedThreadPool(threads);
        for (String repoName : partToSize.getFirst().keySet()) {
            ConcurrentHashMap<String, Integer> lineToCount = new ConcurrentHashMap<>();
            repoToCount.put(repoName, lineToCount);
            partToSize.getFirst().get(repoName)
                    .parallelStream()
                    .forEach((fileToSize) -> workerService.submit(() -> {
                        logger.log("Counting lines in " + repoName + "/" + fileToSize.getFirst());
                            try {
                                String fileString = Helper.makeRequest("/repos/" +
                                        repoName + "/contents/" + fileToSize.getFirst()
                                );

                                JSONObject fileObject = new JSONObject(fileString);
                                if ("base64".equals(fileObject.get("encoding"))) {
                                    String fileContent = new String(
                                            Base64.getMimeDecoder().decode((String) fileObject.get("content"))
                                    );

                                    for (String line : fileContent.split("(\r\n)|\r|\n")) {
                                        line = line.trim();
                                        lineToCount.putIfAbsent(line, 0);
                                        lineToCount.computeIfPresent(line, (l, oldCount) -> oldCount + 1);
                                    }
                                } else {
                                    logger.log("Error encoding is not base64 for: " +
                                            repoName + "/" + fileToSize.getFirst()
                                    );
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                logger.log("Error getting file contents for: " +
                                        repoName + "/" + fileToSize.getFirst()
                                );
                            }
                        })
                );
        }
        workerService.shutdown();
        try {
            workerService.awaitTermination(20L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
            logger.log("Reached timeout for part " + partName);
        }

        String resultName = partName.replace(".part", ".result");
        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(resultName))
        ) {
            outputStream.writeObject(repoToCount);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            logger.log("Error writing result " + resultName + " in Counter");
        }

        logger.log("Finished counting " + resultName);
        processResult.accept(resultName);
    }

    public static void main(String[] args) {


    }

    private static void printUsage() {
        System.out.println("Usage: <directory_with_parts> [threads]");
    }
}
