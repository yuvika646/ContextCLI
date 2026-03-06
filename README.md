# ContextCLI

**Local-first Retrieval-Augmented Generation (RAG) CLI tool.**  
Index your codebase, then chat with it offline — powered by a local LLM via Ollama.

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 + Spring Shell |
| AI Orchestration | Spring AI 1.0 |
| Local LLM | Ollama (`llama3.2` chat · `nomic-embed-text` embeddings) |
| Vector DB | PGVector (PostgreSQL 16 via Docker) |
| Document Parsing | Apache Tika |
| Native Binary | GraalVM Native Image |

---

## Prerequisites

| Requirement | Install |
|---|---|
| **Java 21+** | [SDKMAN](https://sdkman.io/) `sdk install java 21-graalce` or [Adoptium](https://adoptium.net/) |
| **GraalVM 21+** | [GraalVM CE](https://www.graalvm.org/) (use `21-graalce` via SDKMAN for one-step install) |
| **Maven 3.9+** | `sdk install maven` or [Apache Maven](https://maven.apache.org/) |
| **Docker** | [Docker Desktop](https://www.docker.com/products/docker-desktop/) |
| **Ollama** | [ollama.com](https://ollama.com/) |

---

## 1 — Start the Infrastructure

### 1.1 PGVector (PostgreSQL)

```bash
docker compose up -d
```

This launches a PostgreSQL 16 container with the `pgvector` extension.  
Connection details (see `application.properties`):

| Property | Value |
|---|---|
| Host | `localhost:5432` |
| Database | `contextcli` |
| User | `contextcli` |
| Password | `contextcli` |

### 1.2 Ollama — Pull the Models

```bash
# Start the Ollama daemon (if not already running)
ollama serve

# Pull the chat model
ollama pull llama3.2

# Pull the embedding model
ollama pull nomic-embed-text
```

---

## 2 — Build & Run (JIT Mode)

```bash
# Compile
mvn clean package -DskipTests

# Run the interactive CLI
java -jar target/contextcli-1.0.0.jar
```

You will see the ContextCLI banner and a `shell:>` prompt.

---

## 3 — CLI Usage

### Index a codebase

```
shell:> index --path /absolute/path/to/your/project
```

or a single file:

```
shell:> index --path ./src/main/java/com/example/MyService.java
```

The command recursively discovers all supported source files, parses them with
Apache Tika, splits them into token-sized chunks, generates embeddings via
`nomic-embed-text` on your local Ollama, and stores everything in PGVector.

### Ask a question

```
shell:> ask --question "How does the authentication flow work?"
```

Spring AI's `QuestionAnswerAdvisor` automatically:
1. Embeds your question.
2. Retrieves the top-5 most relevant chunks from PGVector.
3. Injects them into the prompt context.
4. Sends the enriched prompt to `llama3.2` via Ollama.
5. Returns the answer.

### Exit

```
shell:> exit
```

---

## 4 — Compile to a Native Image (GraalVM)

Building a native executable gives you **instant CLI startup** (~50 ms vs ~3 s).

### Step-by-step terminal commands

```bash
# ① Make sure JAVA_HOME points to GraalVM 21+
java -version
# Expected: GraalVM CE 21.x or Liberica NIK 21.x

# ② Install the native-image tool (GraalVM CE)
gu install native-image          # skip if already present

# ③ Build the native executable (takes 3-7 minutes on first run)
mvn clean -Pnative native:compile -DskipTests

# ④ Run the native binary
#    Linux / macOS:
./target/contextcli

#    Windows:
target\contextcli.exe
```

> **Note on Apache Tika & native image:**  
> Tika uses heavy reflection and service-loader patterns. If you hit
> `ClassNotFoundException` or `MissingReflectionRegistrationError` at runtime,
> add GraalVM reachability metadata:
>
> ```bash
> # Run the app with the tracing agent to auto-collect metadata
> java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image \
>      -jar target/contextcli-1.0.0.jar
> # (exercise both `index` and `ask` commands, then exit)
>
> # Rebuild with the generated hints
> mvn clean -Pnative native:compile -DskipTests
> ```

---

## 5 — Project Structure

```
ContextCLI/
├── pom.xml                              # Maven build with Spring AI, Shell, GraalVM
├── docker-compose.yml                   # PGVector PostgreSQL container
└── src/main/
    ├── java/com/contextcli/
    │   ├── ContextCliApplication.java   # Spring Boot entry point
    │   └── commands/
    │       └── ContextCliCommands.java  # @ShellComponent (index + ask)
    └── resources/
        ├── application.properties       # Ollama, PGVector, Shell config
        └── banner.txt                   # ASCII art banner
```

---

## Supported File Types

The `index` command processes files with the following extensions:

`.java` `.py` `.js` `.ts` `.jsx` `.tsx` `.go` `.rs` `.rb` `.cpp` `.c` `.h`
`.hpp` `.cs` `.kt` `.scala` `.swift` `.php` `.html` `.css` `.scss` `.sql`
`.xml` `.json` `.yaml` `.yml` `.toml` `.md` `.txt` `.sh` `.bash` `.ps1`
`.gradle` `.properties` `.cfg` `.ini` `.env` `.dockerfile` `.tf` and more.

It automatically **skips** `.git`, `node_modules`, `target`, `build`, `dist`,
`__pycache__`, `.idea`, `.vscode` and similar directories.

---

## License

MIT