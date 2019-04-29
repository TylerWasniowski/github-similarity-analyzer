# GITHUB-SIMILARITY-ANALYZER ANALYZER

## Installation

Create your config:

```bash
cd "analyzer/src"
cp "Config.java.example" "Config.java"
```

Then, replace these lines in Config.java with your GitHub API keys:

```java
public static final String GITHUB_CLIENT_ID  = "xxxx";
public static final String GITHUB_CLIENT_SECRET = "xxxx";
```

## Compiling

```bash
cd analyzer
javac -cp "src\json-20180813.jar;" -d "compiled" "src\*.java"
```

## Run Full Pipeline

This will split the job into parts, count the lines in each part, and then merge each part.

```bash
cd "analyzer/compiled"
java -cp ".;..\src\json-20180813.jar;" Main <job_filepath> [part_size_in_bytes]
```

## Run Splitter Only

This will split the job into parts.

```bash
cd "analyzer/compiled"
java -cp ".;..\src\json-20180813.jar;" Splitter <job_filepath> [part_size_in_bytes]
```

## Run Counter Only

This will count the number of lines each part of a given directory.

```bash
cd "analyzer/compiled"
java -cp ".;..\src\json-20180813.jar;" Counter <directory_with_parts> [threads]
```

## Run Merger Only

This will merge each part in a given directory.

```bash
cd "analyzer/compiled"
java Merger <directory_with_parts> [threads]
```
