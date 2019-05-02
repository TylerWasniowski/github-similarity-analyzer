import sun.rmi.runtime.Log;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class Merger {
    private Logger logger;
    private int threads;

    public Merger(Logger logger) {
        this.logger = logger;
        this.threads = Config.DEFAULT_MERGER_THREADS;
    }

    public Merger(Logger logger, int threads) {
        this(logger);
        this.threads = threads;
    }

    public void mergeResults(String resultName1, String resultName2) {
        mergeResults(resultName1, resultName2, (resultName) ->
                logger.log("Finished merging results " + resultName1 + " and " + resultName2 + " to " + resultName)
        );
    }

    public void mergeResults(String resultName1, String resultName2, Consumer<String> processResult) {
        logger.log("Starting merging " + resultName1 + " and " + resultName2);

        ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> repoToCount = null;
        try (ObjectInputStream inputStream1 = new ObjectInputStream(new FileInputStream(resultName1))) {
            //noinspection unchecked
            ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> repoToCount1 = (ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>>) inputStream1.readObject();

            try (ObjectInputStream inputStream2 = new ObjectInputStream(new FileInputStream(resultName2))) {
                @SuppressWarnings("unchecked")
                ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> repoToCount2 =
                        (ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>>) inputStream2.readObject();


                ExecutorService executor = Executors.newFixedThreadPool(threads);
                repoToCount2
                        .keySet()
                        .parallelStream()
                        .forEach((repoName2) -> {
                            repoToCount1.computeIfAbsent(repoName2, (repoName) -> new ConcurrentHashMap<>());

                            ConcurrentHashMap<String, Integer> lineToCount1 = repoToCount1.get(repoName2);
                            ConcurrentHashMap<String, Integer> lineToCount2 = repoToCount2.get(repoName2);
                            lineToCount2
                                    .keySet()
                                    .parallelStream()
                                    .map((line2) -> executor.submit(() -> {
                                        lineToCount1.putIfAbsent(line2, 0);
                                        lineToCount1.computeIfPresent(line2,
                                                (line, count1) -> lineToCount2.get(line2) + count1
                                        );
                                    }))
                                    .forEach(future -> {
                                        try {
                                            future.get();
                                        } catch (InterruptedException | ExecutionException e) {
                                            e.printStackTrace();
                                            logger.log("Error merging results " +
                                                    resultName1 + " and " + resultName2
                                            );
                                        }
                                    });
                        });

                repoToCount = repoToCount1;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                logger.log("Error reading result " + resultName2 + " in Merger");
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            logger.log("Error reading result " + resultName1 + " in Merger");
        }


        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(resultName1))) {
            outputStream.writeObject(repoToCount);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            logger.log("Error writing result " + resultName1 + " in Merger");
        }

        logger.log("Finished merging results " + resultName1 + " and " + resultName2);
        processResult.accept(resultName1);
    }

    public static void main(String[] args) {
        Logger logger;
        try {
            logger = new Logger("splitter_log.txt");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        int threads = Config.DEFAULT_MERGER_THREADS;
        //noinspection Duplicates
        if (args.length >= 2)
        {
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

        Merger merger = new Merger(logger, threads);
        File folder = new File(args[0]);
        logger.log("Finished merging results into: " +
                Arrays.stream(Objects.requireNonNull(folder.listFiles()))
                .filter(file -> file.isFile() && file.getName().endsWith(".result"))
                .reduce((result1, result2) -> {
                    merger.mergeResults(result1.getName(), result2.getName());
                    return result1;
                })
                .get()
                .getName()
        );
    }

    private static void printUsage() {
        System.out.println("Usage: <directory_with_parts> [threads]");
    }

}
