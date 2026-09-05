package com.itswy.paicodingai.rag.splitter;

import com.itswy.paicodingai.file.parser.BlockType;
import com.itswy.paicodingai.file.parser.ChunkDraft;
import com.itswy.paicodingai.file.parser.ContentBlock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一内容块的父子切片器。
 *
 * <p>父块用于恢复章节上下文，子块才进入检索索引。不同内容类型使用不同的边界策略，
 * 避免把表格、代码和列表当成普通字符串从中间截断。</p>
 */
@Component
public class HierarchicalChunker {

    @Value("${rag.chunk.parent-max-chars:6000}")
    private int parentMaxChars = 6000;

    @Value("${rag.chunk.child-max-chars:1800}")
    private int childMaxChars = 1800;

    @Value("${rag.chunk.child-overlap-chars:240}")
    private int childOverlapChars = 240;

    public List<ChunkDraft> chunk(List<ContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }

        Map<String, List<ContentBlock>> sections = new LinkedHashMap<>();
        for (ContentBlock block : blocks) {
            if (block == null || block.getContent() == null || block.getContent().isBlank()) {
                continue;
            }
            String key = firstNonBlank(block.getParentId(), block.getSectionPath(), "__root__");
            sections.computeIfAbsent(key, ignored -> new ArrayList<>()).add(block);
        }

        List<ChunkDraft> result = new ArrayList<>();
        for (Map.Entry<String, List<ContentBlock>> entry : sections.entrySet()) {
            result.addAll(chunkSection(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private List<ChunkDraft> chunkSection(String sectionKey, List<ContentBlock> blocks) {
        List<ParentSegment> segments = new ArrayList<>();
        List<ContentBlock> current = new ArrayList<>();
        int currentLength = 0;
        int segmentNo = 0;

        for (ContentBlock block : blocks) {
            int blockLength = renderedBlock(block).length();
            if (!current.isEmpty() && currentLength + blockLength > parentMaxChars) {
                segments.add(new ParentSegment(sectionKey + "#" + segmentNo++, current));
                current = new ArrayList<>();
                currentLength = 0;
            }
            current.add(block);
            currentLength += blockLength;
        }
        if (!current.isEmpty()) {
            segments.add(new ParentSegment(sectionKey + "#" + segmentNo, current));
        }

        List<ChunkDraft> result = new ArrayList<>();
        for (ParentSegment segment : segments) {
            List<ContentBlock> segmentBlocks = segment.blocks();
            String sectionPath = segmentBlocks.stream()
                    .map(ContentBlock::getSectionPath)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst().orElse("");
            String parentContent = segmentBlocks.stream()
                    .map(this::renderedBlock)
                    .reduce((left, right) -> left + "\n\n" + right)
                    .orElse("");
            int pageStart = segmentBlocks.stream().mapToInt(ContentBlock::getPageStart)
                    .filter(page -> page > 0).min().orElse(0);
            int pageEnd = segmentBlocks.stream().mapToInt(ContentBlock::getPageEnd)
                    .filter(page -> page > 0).max().orElse(pageStart);

            Map<String, Object> parentMetadata = new LinkedHashMap<>();
            parentMetadata.put("blockCount", segmentBlocks.size());
            parentMetadata.put("sectionPath", sectionPath);
            result.add(ChunkDraft.builder()
                    .role(ChunkDraft.ROLE_PARENT)
                    .parentKey(segment.parentKey())
                    .blockType(BlockType.TEXT)
                    .content(parentContent)
                    .pageStart(pageStart)
                    .pageEnd(pageEnd)
                    .sectionPath(sectionPath)
                    .searchable(false)
                    .metadata(parentMetadata)
                    .build());

            for (ContentBlock block : segmentBlocks) {
                if (!block.isSearchable()) {
                    continue;
                }
                for (String part : splitBlock(block)) {
                    if (part.isBlank()) {
                        continue;
                    }
                    Map<String, Object> childMetadata = new LinkedHashMap<>();
                    if (block.getMetadata() != null) {
                        childMetadata.putAll(block.getMetadata());
                    }
                    childMetadata.put("sectionPath", firstNonBlank(block.getSectionPath(), sectionPath, ""));
                    childMetadata.put("blockType", block.getType() == null ? BlockType.TEXT.name() : block.getType().name());
                    result.add(ChunkDraft.builder()
                            .role(ChunkDraft.ROLE_CHILD)
                            .parentKey(segment.parentKey())
                            .sourceBlockId(block.getBlockId())
                            .blockType(block.getType() == null ? BlockType.TEXT : block.getType())
                            .content(enrichChildContent(block, part, sectionPath))
                            .pageStart(block.getPageStart())
                            .pageEnd(block.getPageEnd())
                            .sectionPath(firstNonBlank(block.getSectionPath(), sectionPath, ""))
                            .searchable(true)
                            .metadata(childMetadata)
                            .build());
                }
            }
        }
        return result;
    }

    private List<String> splitBlock(ContentBlock block) {
        String content = block.getContent().trim();
        if (content.length() <= childMaxChars) {
            return List.of(content);
        }

        return switch (block.getType() == null ? BlockType.TEXT : block.getType()) {
            case TABLE -> splitTable(block, content);
            case CODE, LIST -> splitByLines(content);
            default -> splitText(content);
        };
    }

    private List<String> splitTable(ContentBlock block, String content) {
        String[] lines = content.split("\\n");
        int prefixCount = lines.length > 0 && lines[0].startsWith("工作表：") ? 3 : Math.min(2, lines.length);
        String prefix = String.join("\n", java.util.Arrays.copyOfRange(lines, 0, prefixCount)).trim();
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder(prefix);
        for (int i = prefixCount; i < lines.length; i++) {
            String line = lines[i];
            if (!current.isEmpty() && current.length() + line.length() + 1 > childMaxChars) {
                parts.add(current.toString().trim());
                current = new StringBuilder(prefix);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts.isEmpty() ? List.of(content) : parts;
    }

    private List<String> splitByLines(String content) {
        String[] lines = content.split("\\n");
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.length() > childMaxChars) {
                if (!current.isEmpty()) {
                    parts.add(current.toString().trim());
                    current.setLength(0);
                }
                for (int start = 0; start < line.length(); start += childMaxChars) {
                    parts.add(line.substring(start, Math.min(start + childMaxChars, line.length())).trim());
                }
                continue;
            }
            if (!current.isEmpty() && current.length() + line.length() + 1 > childMaxChars) {
                parts.add(current.toString().trim());
                String overlap = tailLines(current.toString(), 2);
                current = new StringBuilder(overlap);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    private List<String> splitText(String content) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int proposedEnd = Math.min(start + childMaxChars, content.length());
            int end = proposedEnd;
            if (proposedEnd < content.length()) {
                int boundary = findBoundary(content, start, proposedEnd);
                if (boundary > start + childMaxChars / 3) {
                    end = boundary;
                }
            }
            String part = content.substring(start, end).trim();
            if (!part.isBlank()) {
                parts.add(part);
            }
            if (end >= content.length()) {
                break;
            }
            int overlap = Math.min(Math.max(0, childOverlapChars), Math.max(0, end - start - 1));
            start = Math.max(start + 1, end - overlap);
        }
        return parts;
    }

    private int findBoundary(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；'
                    || c == '.' || c == '!' || c == '?' || c == ';') {
                return i + 1;
            }
        }
        return end;
    }

    private String tailLines(String text, int count) {
        String[] lines = text.split("\\n");
        int start = Math.max(0, lines.length - count);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length));
    }

    private String enrichChildContent(ContentBlock block, String content, String fallbackSectionPath) {
        String sectionPath = firstNonBlank(block.getSectionPath(), fallbackSectionPath, "");
        String type = block.getType() == null ? BlockType.TEXT.name() : block.getType().name();
        StringBuilder result = new StringBuilder();
        if (!sectionPath.isBlank()) {
            result.append("章节：").append(sectionPath).append('\n');
        }
        result.append("类型：").append(type).append('\n').append(content);
        return result.toString().trim();
    }

    private String renderedBlock(ContentBlock block) {
        String type = block.getType() == null ? BlockType.TEXT.name() : block.getType().name();
        StringBuilder result = new StringBuilder();
        if (block.getSectionPath() != null && !block.getSectionPath().isBlank()) {
            result.append("章节：").append(block.getSectionPath()).append('\n');
        }
        result.append("类型：").append(type).append('\n').append(block.getContent().trim());
        return result.toString();
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private record ParentSegment(String parentKey, List<ContentBlock> blocks) {}
}
