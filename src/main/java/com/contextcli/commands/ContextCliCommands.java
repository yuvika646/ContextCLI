package com.contextcli.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * CLI commands for ContextCLI.
 *
 * <ul>
 *   <li><b>index</b> – ingests source files into the PGVector store.</li>
 *   <li><b>ask</b>  – queries the local LLM with RAG context from the vector store.</li>
 * </ul>
 */
@ShellComponent
public class ContextCliCommands {

    private static final Logger log = LoggerFactory.getLogger(ContextCliCommands.class);

    /* ── file extensions treated as "source code" ── */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".jsx", ".tsx",
            ".go", ".rs", ".rb", ".cpp", ".c", ".h", ".hpp",
            ".cs", ".kt", ".kts", ".scala", ".swift", ".php",
            ".html", ".css", ".scss", ".less",
            ".sql", ".graphql", ".proto",
            ".xml", ".json", ".yaml", ".yml", ".toml",
            ".md", ".txt", ".rst",
            ".sh", ".bash", ".zsh", ".ps1", ".bat", ".cmd",
            ".gradle", ".sbt", ".cmake",
            ".properties", ".cfg", ".ini", ".env",
            ".dockerfile", ".tf", ".hcl",
            ".makefile", ".mk"
    );

    /** Directories that are never useful to index. */
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", ".svn", ".hg",
            "node_modules", "__pycache__", ".venv", "venv",
            "target", "build", "dist", "out", "bin",
            ".idea", ".vscode", ".gradle", ".mvn"
    );

    private static final String SYSTEM_PROMPT = """
            You are **ContextCLI**, an expert code-analysis assistant.
            You answer questions about a developer's codebase using *only* the
            retrieved source-code context provided below.

            Rules:
            1. Reference specific file names and quote relevant snippets.
            2. If the context is insufficient to answer, say so explicitly.
            3. Never fabricate code that is not present in the context.
            4. Keep answers concise but thorough.
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public ContextCliCommands(VectorStore vectorStore,
                              ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    // ───────────────────────── index ─────────────────────────

    @ShellMethod(key = "index", value = "Index source files from a file or directory path into the vector store.")
    public String index(
            @ShellOption(value = {"--path", "-p"},
                    help = "Absolute or relative path to a file or directory to index")
            String path) {

        Path target = Path.of(path).toAbsolutePath().normalize();

        if (!Files.exists(target)) {
            return "✗ Path does not exist: " + target;
        }

        List<Path> files = collectFiles(target);

        if (files.isEmpty()) {
            return "✗ No supported source files found at: " + target;
        }

        log.info("Found {} file(s) to index under {}", files.size(), target);

        TokenTextSplitter splitter = new TokenTextSplitter();
        int indexed = 0;
        int failed  = 0;
        int totalChunks = 0;

        for (Path file : files) {
            try {
                Resource resource = new FileSystemResource(file.toFile());
                TikaDocumentReader reader = new TikaDocumentReader(resource);
                List<Document> documents = reader.get();

                // Enrich each document with source metadata
                String filePath   = file.toString();
                String fileName   = file.getFileName().toString();
                String relativePath = target.relativize(file).toString();

                for (Document doc : documents) {
                    doc.getMetadata().put("source", filePath);
                    doc.getMetadata().put("file_name", fileName);
                    doc.getMetadata().put("relative_path", relativePath);
                }

                List<Document> chunks = splitter.apply(documents);
                vectorStore.add(chunks);

                totalChunks += chunks.size();
                indexed++;
                System.out.printf("  ✓ [%d/%d] %s  (%d chunk%s)%n",
                        indexed + failed, files.size(), relativePath,
                        chunks.size(), chunks.size() == 1 ? "" : "s");

            } catch (Exception ex) {
                failed++;
                log.warn("Failed to index {}: {}", file.getFileName(), ex.getMessage());
                System.out.printf("  ✗ [%d/%d] %s  – %s%n",
                        indexed + failed, files.size(),
                        file.getFileName(), ex.getMessage());
            }
        }

        return String.format(
                "%n── Indexing complete ──%n" +
                "  Files indexed : %d%n" +
                "  Files failed  : %d%n" +
                "  Total chunks  : %d%n",
                indexed, failed, totalChunks);
    }

    // ───────────────────────── ask ───────────────────────────

    @ShellMethod(key = "ask", value = "Ask a question about your indexed codebase.")
    public String ask(
            @ShellOption(value = {"--question", "-q"},
                    help = "Your natural-language question (wrap in quotes if multi-word)")
            String question) {

        log.info("Question received – retrieving context and querying LLM …");

        try {
            RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(VectorStoreDocumentRetriever.builder()
                            .vectorStore(vectorStore)
                            .topK(5)
                            .build())
                    .build();

            String answer = chatClient.prompt()
                    .user(question)
                    .advisors(advisor)
                    .call()
                    .content();

            return "\n" + answer;

        } catch (Exception ex) {
            log.error("LLM query failed", ex);
            return "✗ Failed to get an answer: " + ex.getMessage()
                    + "\n  Make sure Ollama is running (ollama serve) and the llama3.2 model is pulled.";
        }
    }

    // ───────────────────── helper methods ────────────────────

    /**
     * Collects all indexable source files under the given path.
     * If {@code target} is a regular file it is returned as-is (if supported).
     */
    private List<Path> collectFiles(Path target) {
        if (Files.isRegularFile(target)) {
            return isSupportedFile(target) ? List.of(target) : List.of();
        }

        try (Stream<Path> walk = Files.walk(target)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> !isInsideExcludedDir(p))
                    .filter(this::isSupportedFile)
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            log.error("Failed to walk directory {}: {}", target, ex.getMessage());
            return List.of();
        }
    }

    private boolean isSupportedFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();

        // Handle extension-less well-known files
        if (name.equals("dockerfile") || name.equals("makefile") || name.equals("jenkinsfile")) {
            return true;
        }

        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /**
     * Returns {@code true} when any segment of the path matches an excluded directory name.
     */
    private boolean isInsideExcludedDir(Path file) {
        for (Path segment : file) {
            if (EXCLUDED_DIRS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }
}
