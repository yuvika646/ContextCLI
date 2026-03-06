<div align="center">

# 🧠 ContextCLI

### Local-first RAG CLI — Chat with your codebase, completely offline.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.2-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring_AI-1.0.0-brightgreen?logo=spring&logoColor=white)](https://spring.io/projects/spring-ai)
[![Ollama](https://img.shields.io/badge/Ollama-llama3.2-black?logo=ollama&logoColor=white)](https://ollama.com/)
[![PGVector](https://img.shields.io/badge/PGVector-PostgreSQL_16-blue?logo=postgresql&logoColor=white)](https://github.com/pgvector/pgvector)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

<br/>

> Index any codebase. Ask natural-language questions. Get answers grounded in your real source code.  
> No cloud. No API keys. No data leaves your machine.

</div>

---

## ✨ Features

- 🔍 **Smart Indexing** — Recursively scans and indexes entire codebases using Apache Tika
- 💬 **Natural Language Q&A** — Ask any question about your code in plain English
- 🔒 **100% Private & Offline** — Powered by local models via Ollama (no internet required)
- ⚡ **RAG Pipeline** — Retrieves only the top-5 relevant chunks, not the whole codebase
- 🗄️ **Vector Storage** — Semantic search via PGVector (PostgreSQL with pgvector extension)
- 🔄 **Always Fresh** — Re-index after any code change in seconds — no fine-tuning needed

---

## 🏗️ Architecture

```
Your Question
     │
     ▼
┌─────────────────────────────┐
│  nomic-embed-text (Ollama)  │  ← embed question
└─────────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│  PGVector Similarity Search │  ← top-5 relevant chunks
└─────────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│  RetrievalAugmentationAdvisor│  ← inject context into prompt
└─────────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│  llama3.2 via Ollama        │  ← generate answer
└─────────────────────────────┘
     │
     ▼
  Answer grounded in your actual code
```

---

## 🧰 Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.2 + Spring Shell 3.3.3 |
| AI Orchestration | Spring AI 1.0.0 |
| Chat Model | Ollama `llama3.2` |
| Embedding Model | Ollama `nomic-embed-text` |
| Vector Database | PGVector on PostgreSQL 16 (Docker) |
| Document Parsing | Apache Tika 3.x |
| RAG Advisor | Spring AI `RetrievalAugmentationAdvisor` |

---

## ⚙️ Prerequisites

| Tool | Version | Link |
|---|---|---|
| Java JDK | 21+ | [Adoptium](https://adoptium.net/) |
| Apache Maven | 3.9+ | [maven.apache.org](https://maven.apache.org/) |
| Docker Desktop | Latest | [docker.com](https://www.docker.com/products/docker-desktop/) |
| Ollama | Latest | [ollama.com](https://ollama.com/) |

> **Windows users:** Docker Desktop requires WSL 2. Run `wsl --update` in an admin PowerShell if Docker fails to start.

---

## 🚀 Getting Started

### Step 1 — Start the Vector Database

```bash
docker compose up -d
```

| Setting | Value |
|---|---|
| Host | `localhost:5432` |
| Database | `contextcli` |
| Username | `contextcli` |
| Password | `contextcli` |

### Step 2 — Pull Ollama Models

```bash
ollama pull llama3.2
ollama pull nomic-embed-text
```

> 💡 Run `ollama run llama3.2 "hi"` once before using the app to pre-load the model into memory.

### Step 3 — Run the Application

```bash
mvn spring-boot:run
```

Wait for the `shell:>` prompt to appear, then start using the commands below.

---

## 💻 Usage

### `index` — Index a codebase

```bash
shell:> index --path .
shell:> index --path C:/Users/you/myproject
```

> **Windows:** Use `.` or forward slashes `/`. Backslashes are escape characters in Spring Shell.

**Sample output:**
```
  ✓ [1/7] pom.xml  (1 chunk)
  ✓ [2/7] docker-compose.yml  (1 chunk)
  ✓ [3/7] README.md  (2 chunks)
  ✓ [4/7] src/main/java/.../ContextCliCommands.java  (3 chunks)

── Indexing complete ──
  Files indexed : 7
  Files failed  : 0
  Total chunks  : 10
```

### `ask` — Ask a question

```bash
shell:> ask --question "What commands does this CLI support?"
shell:> ask --question "How does the indexing pipeline work?"
shell:> ask --question "What dependencies does this project use?"
```

### `exit` — Exit the shell

```bash
shell:> exit
```

---

## 📁 Project Structure

```
ContextCLI/
├── pom.xml                                   # Maven build (Spring AI, RAG, commons-io fix)
├── docker-compose.yml                        # PGVector PostgreSQL 16 container
└── src/main/
    ├── java/com/contextcli/
    │   ├── ContextCliApplication.java        # Spring Boot entry point
    │   ├── commands/
    │   │   └── ContextCliCommands.java       # index + ask shell commands
    │   └── config/
    │       └── OllamaHttpConfig.java         # Blocking HTTP client fix
    └── resources/
        ├── application.properties            # Ollama, PGVector, timeout config
        └── banner.txt                        # ASCII art banner
```

---

## 📄 Supported File Types

| Category | Extensions |
|---|---|
| JVM | `.java` `.kt` `.scala` `.groovy` |
| Python | `.py` |
| JavaScript / TypeScript | `.js` `.ts` `.jsx` `.tsx` |
| Systems | `.go` `.rs` `.cpp` `.c` `.h` `.cs` `.swift` |
| Web | `.html` `.css` `.scss` |
| Data / Config | `.sql` `.json` `.yaml` `.yml` `.toml` `.xml` `.properties` `.env` |
| Docs | `.md` `.txt` `.rst` |
| DevOps / IaC | `.sh` `.ps1` `.dockerfile` `.tf` `.gradle` |

**Auto-skipped:** `.git` `node_modules` `target` `build` `dist` `__pycache__` `.idea` `.vscode`

---

## 🆚 Why RAG?

| Without RAG | With RAG (ContextCLI) |
|---|---|
| LLM guesses from training data | Answers from your actual code |
| Hallucinated APIs & function names | References real file names & snippets |
| Unaware of private codebases | Instantly aware of any indexed project |
| Entire codebase exceeds context limit | Only top-5 relevant chunks injected |
| Requires expensive fine-tuning to update | Re-index in seconds after any change |

---

## 📝 Notes

| Note | Detail |
|---|---|
| **Slow first ask** | `llama3.2` takes 1–2 min to load on first use. Pre-warm with `ollama run llama3.2 "hi"` |
| **Windows paths** | Always use `.` or forward slashes `/` in `index --path` |
| **WSL 2** | Docker Desktop on Windows requires WSL 2 — run `wsl --update` in admin PowerShell |
| **Model sizes** | `llama3.2` ≈ 2 GB · `nomic-embed-text` ≈ 274 MB |

---

## License

MIT © 2026
