# GITHUB-SIMILARITY-ANALYZER

## Installation

First, clone the repo via git:

```bash
git clone --depth=1 https://github.com/tylerwasniowski/github-similarity-analyzer.git "github-similarity-analyzer"
```

And then install dependencies with yarn:

```bash
cd "github-similarity-analyzer"
yarn install
```

Then create your analyzer config:

```bash
cd "github-similarity-analyzer/packages/analyzer/src"
cp "Config.java.example" "Config.java"
```

Finally, replace these lines in Config.java with your GitHub API keys:

```java
public static final String GITHUB_CLIENT_ID  = "xxxx";
public static final String GITHUB_CLIENT_SECRET = "xxxx";
```

## Run in Development Mode

It is recommended to run the backend and frontend in separate terminal sessions.

See [the instructions in the frontend package](./packages/frontend#github-similarity-analyzer-frontend) for starting the frontend.

See [the instructions in the backend package](./packages/backend#github-similarity-analyzer-backend) for starting the backend.

If you would rather run both frontend and backend in a single terminal session, you can run:

```bash
yarn dev
```

## Run in Production Mode

First build the package by running:

```bash
yarn build
```

Then start the application in production by running:

```bash
yarn start
```

Alternatively, to use just the analyzer without the web interface, follow [the instructions in the analyzer package](./packages/analyzer#github-similarity-analyzer-analyzer).
