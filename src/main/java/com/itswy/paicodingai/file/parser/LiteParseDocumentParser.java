package com.itswy.paicodingai.file.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * LiteParse CLI 适配器。输出统一页面内容块，解析失败由调用方回退 PDFBox。
 */
@Slf4j
@Component
public class LiteParseDocumentParser {

    private static final Pattern HEADING = Pattern.compile(
            "^(第\\s*\\d+\\s*[章节篇]|\\d+(?:\\.\\d+){0,5})[\\s:：].+$");

    private final ObjectMapper objectMapper;
    private final OcrService ocrService;

    @Value("${file.parsing.liteparse.command:lit}")
    private String command;
    @Value("${file.parsing.liteparse.ocr-enabled:true}")
    private boolean ocrEnabled;
    @Value("${file.parsing.liteparse.ocr-language:chi_sim+eng}")
    private String ocrLanguage;
    @Value("${file.parsing.liteparse.max-pages:1000}")
    private int maxPages;
    @Value("${file.parsing.liteparse.dpi:150}")
    private int dpi;
    @Value("${file.parsing.liteparse.num-workers:0}")
    private int workers;
    @Value("${file.parsing.liteparse.timeout-seconds:300}")
    private long timeoutSeconds;
    @Value("${aliyun.ocr.enabled:false}")
    private boolean aliyunOcrEnabled;
    @Value("${aliyun.ocr.callback-token:}")
    private String callbackToken;
    @Value("${server.port:8081}")
    private int serverPort;

    public LiteParseDocumentParser(ObjectMapper objectMapper, OcrService ocrService) {
        this.objectMapper = objectMapper;
        this.ocrService = ocrService;
    }

    public ParseResult parse(File file) {
        Path input = null;
        Path output = null;
        Path stdout = null;
        Path stderr = null;
        try {
            input = Files.createTempFile("paicoding-liteparse-", ".pdf");
            output = Files.createTempFile("paicoding-liteparse-", ".json");
            stdout = Files.createTempFile("paicoding-liteparse-", ".out");
            stderr = Files.createTempFile("paicoding-liteparse-", ".err");
            Files.copy(file.toPath(), input, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(input, output))
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile());
            Process process = processBuilder.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("LiteParse 解析超时");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("LiteParse 解析失败: " + readLog(stderr));
            }
            return mapResult(file, objectMapper.readTree(output.toFile()));
        } catch (Exception e) {
            log.warn("LiteParse 解析失败，将由 PDFBox 回退: file={}, error={}", file.getName(), e.getMessage());
            return new ParseResult("LiteParse 解析失败: " + e.getMessage());
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
            deleteQuietly(stdout);
            deleteQuietly(stderr);
        }
    }

    private List<String> buildCommand(Path input, Path output) {
        List<String> args = new ArrayList<>();
        args.add(command);
        args.add("parse");
        args.add(input.toString());
        args.add("--format");
        args.add("json");
        args.add("--output");
        args.add(output.toString());
        args.add("--max-pages");
        args.add(String.valueOf(maxPages));
        args.add("--dpi");
        args.add(String.valueOf(dpi));
        if (!ocrEnabled) {
            args.add("--no-ocr");
        } else {
            args.add("--ocr-language");
            args.add(ocrLanguage);
            if (aliyunOcrEnabled) {
                args.add("--ocr-server-url");
                String endpoint = "http://127.0.0.1:" + serverPort + "/api/v1/internal/ocr/liteparse";
                if (callbackToken != null && !callbackToken.isBlank()) {
                    endpoint += "?token=" + java.net.URLEncoder.encode(callbackToken, StandardCharsets.UTF_8);
                }
                args.add(endpoint);
            }
        }
        if (workers > 0) {
            args.add("--num-workers");
            args.add(String.valueOf(workers));
        }
        args.add("--quiet");
        return args;
    }

    private ParseResult mapResult(File file, JsonNode root) {
        JsonNode pages = root.path("pages");
        if (!pages.isArray()) {
            throw new IllegalStateException("LiteParse 输出缺少 pages 数组");
        }
        List<ContentBlock> blocks = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        Deque<String> sections = new ArrayDeque<>();
        Deque<Integer> levels = new ArrayDeque<>();
        String currentSectionId = null;
        String title = null;
        int pageNumber = 0;
        for (JsonNode page : pages) {
            pageNumber++;
            int pageNo = page.path("page").asInt(pageNumber);
            String text = page.path("text").asText("").trim();
            if (text.isBlank()) {
                continue;
            }
            if (!content.isEmpty()) {
                content.append('\n');
            }
            content.append(text);
            StringBuilder paragraph = new StringBuilder();
            for (String line : text.replace('\r', '\n').split("\\n")) {
                String clean = line.trim();
                if (clean.isBlank()) {
                    addText(blocks, paragraph, pageNo, currentSectionId, sectionPath(sections));
                    continue;
                }
                if (HEADING.matcher(clean).matches()) {
                    addText(blocks, paragraph, pageNo, currentSectionId, sectionPath(sections));
                    int level = clean.startsWith("第") ? 1 : Math.max(1, clean.split("[\\s:：]", 2)[0].split("\\.").length);
                    while (!levels.isEmpty() && levels.peek() >= level) { levels.pop(); sections.pop(); }
                    levels.push(level); sections.push(clean); currentSectionId = "section-" + (blocks.size() + 1);
                    blocks.add(ContentBlock.builder().blockId("lite-block-" + (blocks.size() + 1))
                            .type(BlockType.HEADING).content(clean).pageStart(pageNo).pageEnd(pageNo)
                            .sectionPath(sectionPath(sections)).parentId(currentSectionId).searchable(false)
                            .metadata(Map.of("level", level, "parser", "liteparse")).build());
                    if (title == null) title = clean;
                } else {
                    if (!paragraph.isEmpty()) paragraph.append(' ');
                    paragraph.append(clean);
                }
            }
            addText(blocks, paragraph, pageNo, currentSectionId, sectionPath(sections));
        }
        if (title == null || title.isBlank()) title = fileNameWithoutExtension(file.getName());
        return new ParseResult(title, content.toString(), blocks, "pdf", file.length(), true);
    }

    private void addText(List<ContentBlock> blocks, StringBuilder text, int page,
                         String parentId, String sectionPath) {
        String value = text.toString().trim();
        if (!value.isBlank()) {
            blocks.add(ContentBlock.builder().blockId("lite-block-" + (blocks.size() + 1))
                    .type(BlockType.TEXT).content(value).pageStart(page).pageEnd(page)
                    .sectionPath(sectionPath).parentId(parentId).metadata(Map.of("parser", "liteparse")).build());
        }
        text.setLength(0);
    }

    private String sectionPath(Deque<String> sections) {
        List<String> values = new ArrayList<>(sections);
        java.util.Collections.reverse(values);
        return String.join(" > ", values);
    }

    private String fileNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String readLog(Path path) {
        try { return Files.readString(path, StandardCharsets.UTF_8).trim(); }
        catch (Exception ignored) { return ""; }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (Exception ignored) { path.toFile().deleteOnExit(); }
    }
}
