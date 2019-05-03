import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
        if (args.length != 1) { 
            printUsage();
            logger.log("Wrong number of arguments");
            logger.close();
            return;
        }


        Splitter splitter = new Splitter(logger);
        Counter counter = new Counter(logger);
        Merger merger = new Merger(logger);

        Set<String> results = new HashSet<>();
        Set<String> unprocessedResults = ConcurrentHashMap.newKeySet();
        splitter.makeParts(args[0], (partName) -> {
            logger.log("Finished making part " + partName);
            counter.countLines(partName, (resultName) -> {
                unprocessedResults.add(resultName);

                handleMerge(merger, results, unprocessedResults, resultName, getOtherPart(results, resultName));
            });
        });
    }

    private static synchronized String getOtherPart(Set<String> results, String resultName) {
        String otherResultName = null;

        if (results.isEmpty()) {
            results.add(resultName);
        } else {
            otherResultName = results.iterator().next();
            results.remove(otherResultName);
        }

        return otherResultName;
    }

    private static void handleMerge(Merger merger, Set<String> results, Set<String> unprocessedResults,
                                    String resultName, String otherResultName) {
        if (otherResultName != null) {
            merger.mergeResults(resultName, otherResultName, (mergeResultName) -> {
                logger.log("Finished merging part " + mergeResultName);

                unprocessedResults.remove(resultName);
                unprocessedResults.remove(otherResultName);
                handleMerge(merger, results, unprocessedResults, mergeResultName, getOtherPart(results, mergeResultName));
            });
        } else if (unprocessedResults.isEmpty() ||
                (unprocessedResults.size() == 1 && unprocessedResults.iterator().next().equals(resultName))) {
            writeScores(resultName);
            writeMostDuplicated(resultName);
        }
    }

    private static void writeScores(String resultName) {
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(resultName))) {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> repoToCount =
                    (ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>>) inputStream.readObject();

            List<Pair<String, Double>> similarityScores = repoToCount
                    .keySet()
                    .parallelStream()
                    .map((repoName) -> {
                        Map<String, Integer> lineToCount = repoToCount.get(repoName);

                        AtomicInteger duplicated = new AtomicInteger(0);
                        AtomicInteger total = new AtomicInteger(0);
                        lineToCount
                                .keySet()
                                .parallelStream()
                                .forEach((line) -> {
                                    boolean[] lineDuplicated = new boolean[1];
                                    lineDuplicated[0] = false;
                                    if (lineToCount.get(line) > 1) lineDuplicated[0] = true;
                                    else {
                                        repoToCount
                                                .keySet()
                                                .parallelStream()
                                                .filter((otherRepoName) -> !repoName.equals(otherRepoName))
                                                .forEach((otherRepoName) -> {
                                                    long n = repoToCount
                                                            .get(otherRepoName)
                                                            .keySet()
                                                            .parallelStream()
                                                            .filter(line::equals)
                                                            .count();
                                                    if (n > 0L) {
                                                        synchronized (lineDuplicated) {
                                                            lineDuplicated[0] = true;
                                                        }
                                                    }
                                                });
                                    }

                                    if (lineDuplicated[0]) duplicated.incrementAndGet();
                                    total.incrementAndGet();
                                });

                        logger.log("Duplicated: " + duplicated);
                        logger.log("Total: " + total);


                        double similarityScore = ((duplicated.intValue() * 1000) / (total.intValue())) / 10.0;

                        return new Pair<>(repoName, similarityScore);
                    })
                    .collect(Collectors.toList());


            String scoresName = resultName.replace(".result", "_scores.txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(scoresName))) {
                for (Pair<String, Double> repoToSimilarityScore : similarityScores) {
                    writer.write(repoToSimilarityScore.getFirst() + ": " + repoToSimilarityScore.getSecond());
                    writer.newLine();
                }
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
                logger.log("Error writing scores");
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            logger.log("Error reading final results in lines writer");
        }

        logger.log("Finished writing scores");
    }

    private static void writeMostDuplicated(String resultName) {
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(resultName))) {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> repoToCount =
                    (ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>>) inputStream.readObject();

            String linesName = resultName.replace(".result", "_lines.txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(linesName))) {
                repoToCount
                        .keySet()
                        .forEach((repoName) -> {
                            try {
                                writer.write("Repo: " + repoName);
                                writer.newLine();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            ConcurrentHashMap<String, Integer> lineToCount = repoToCount.get(repoName);

                            //noinspection SuspiciousMethodCalls
                            lineToCount
                                    .keySet()
                                    .stream()
                                    .sorted(Comparator.comparingInt(lineToCount::get).reversed())
                                    .forEach((line) -> {
                                        try {
                                            writer.write(line + ": " + lineToCount.get(line));
                                            writer.newLine();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    });

                            try {
                                writer.newLine();
                                writer.newLine();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });

                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
                logger.log("Error writing lines");
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            logger.log("Error reading final results in lines writer");
        }

        logger.log("Finished writing lines");
    }

    private static void printUsage() {
        System.out.println("Usage: <job_filepath> [show_scores (0 or 1] [show_lines");
    }
}
