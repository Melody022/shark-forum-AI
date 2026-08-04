package com.itswy.paicodingai.rag.splitter;

import com.hankcs.hanlp.seg.Segment;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.tokenizer.StandardTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义感知文本切分器
 *
 * 参考派聪明（PaiSmart）实现：
 * 四层级切分策略，保证每个Chunk语义完整
 *
 * 第一层：段落切分（\n\n）
 * 第二层：句子切分
 * 第三层：HanLP分词边界切分
 * 第四层：字符切分（兜底）
 */
@Slf4j
@Component
public class SemanticTextSplitter {

    /** 默认Chunk大小（字符） */
    private static final int DEFAULT_CHUNK_SIZE = 512;

    /** 默认重叠区域（字符） */
    private static final int DEFAULT_OVERLAP = 100;

    /** 最小Chunk大小 */
    private static final int MIN_CHUNK_SIZE = 100;

    /** 最大Chunk大小 */
    private static final int MAX_CHUNK_SIZE = 1000;

    /**
     * 切分文本
     *
     * @param content 原始文本
     * @return 切分后的Chunk列表
     */
    public List<String> split(String content) {
        return split(content, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * 切分文本（自定义参数）
     *
     * @param content 原始文本
     * @param chunkSize Chunk大小
     * @param overlap 重叠区域大小
     * @return 切分后的Chunk列表
     */
    public List<String> split(String content, int chunkSize, int overlap) {
        if (content == null || content.trim().isEmpty()) {
            return List.of();
        }

        // 参数校验
        chunkSize = Math.max(MIN_CHUNK_SIZE, Math.min(MAX_CHUNK_SIZE, chunkSize));
        overlap = Math.min(overlap, chunkSize / 3); // 重叠不超过chunkSize的1/3

        log.info("开始文本切分，原始长度: {}，Chunk大小: {}，重叠: {}",
                 content.length(), chunkSize, overlap);

        // 第一层：段落切分
        List<String> paragraphs = splitByParagraph(content);

        // 第二层：句子切分（处理超长段落）
        List<String> chunks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.length() <= chunkSize) {
                if (paragraph.trim().length() >= MIN_CHUNK_SIZE) {
                    chunks.add(paragraph.trim());
                }
            } else {
                // 段落太长，按句子切分
                List<String> sentenceChunks = splitBySentence(paragraph, chunkSize);
                chunks.addAll(sentenceChunks);
            }
        }

        // 添加语义感知重叠
        List<String> result = addSemanticOverlap(chunks, overlap);

        log.info("文本切分完成，生成Chunk数量: {}", result.size());
        return result;
    }

    /**
     * 第一层：段落切分（按双换行符）
     */
    private List<String> splitByParagraph(String content) {
        List<String> paragraphs = new ArrayList<>();

        // 按双换行符切分
        String[] parts = content.split("\n\n+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }

        // 如果没有双换行符，按单换行符切分
        if (paragraphs.isEmpty()) {
            parts = content.split("\n+");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    paragraphs.add(trimmed);
                }
            }
        }

        // 如果还是没有，返回整个内容
        if (paragraphs.isEmpty()) {
            paragraphs.add(content.trim());
        }

        return paragraphs;
    }

    /**
     * 第二层：句子切分
     */
    private List<String> splitBySentence(String paragraph, int chunkSize) {
        List<String> chunks = new ArrayList<>();

        // 按中文标点切分（句号、问号、感叹号、分号）
        String[] sentences = paragraph.split("(?<=[。！？；.!?;])");

        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) {
                continue;
            }

            // 如果当前Chunk加上新句子超过chunkSize
            if (currentChunk.length() + sentence.length() > chunkSize) {
                // 保存当前Chunk
                if (currentChunk.length() >= MIN_CHUNK_SIZE) {
                    chunks.add(currentChunk.toString().trim());
                }
                currentChunk = new StringBuilder();
            }

            // 如果单个句子就超过chunkSize，使用HanLP切分
            if (sentence.length() > chunkSize) {
                // 先保存当前Chunk
                if (currentChunk.length() >= MIN_CHUNK_SIZE) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }

                // 第三层：HanLP分词边界切分
                List<String> wordChunks = splitByWord(sentence, chunkSize);
                chunks.addAll(wordChunks);
            } else {
                currentChunk.append(sentence);
            }
        }

        // 保存最后一个Chunk
        if (currentChunk.length() >= MIN_CHUNK_SIZE) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 第三层：HanLP分词边界切分
     *
     * 使用HanLP分词，在词的边界处切分，保证不会把一个词从中间劈开
     */
    private List<String> splitByWord(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();

        // HanLP分词
        List<Term> terms = StandardTokenizer.segment(text);

        StringBuilder currentChunk = new StringBuilder();
        StringBuilder currentWord = new StringBuilder();

        for (Term term : terms) {
            String word = term.word;

            // 如果当前Chunk加上新词超过chunkSize
            if (currentChunk.length() + word.length() > chunkSize) {
                // 保存当前Chunk
                if (currentChunk.length() >= MIN_CHUNK_SIZE) {
                    chunks.add(currentChunk.toString().trim());
                }
                currentChunk = new StringBuilder();
            }

            currentChunk.append(word);
        }

        // 保存最后一个Chunk
        if (currentChunk.length() >= MIN_CHUNK_SIZE) {
            chunks.add(currentChunk.toString().trim());
        }

        // 如果还是太大，使用第四层：字符切分
        List<String> result = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.length() > chunkSize) {
                // 第四层：字符切分（兜底）
                result.addAll(splitByChar(chunk, chunkSize));
            } else {
                result.add(chunk);
            }
        }

        return result;
    }

    /**
     * 第四层：字符切分（兜底方案）
     */
    private List<String> splitByChar(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start = end;
        }

        return chunks;
    }

    /**
     * 添加语义感知重叠
     *
     * 参考派聪明实现：
     * - 不是简单地取前一个Chunk的最后N个字符
     * - 而是使用HanLP分词找到合适的句子边界
     * - 保证重叠部分不会把一个词从中间劈开
     */
    private List<String> addSemanticOverlap(List<String> chunks, int overlap) {
        if (chunks.size() <= 1 || overlap <= 0) {
            return chunks;
        }

        List<String> result = new ArrayList<>();
        result.add(chunks.get(0)); // 第一个Chunk不需要重叠

        for (int i = 1; i < chunks.size(); i++) {
            String prevChunk = chunks.get(i - 1);
            String currentChunk = chunks.get(i);

            // 从上一个Chunk的末尾提取重叠部分
            String overlapText = extractOverlapText(prevChunk, overlap);

            // 将重叠部分添加到当前Chunk的开头
            String newChunk = overlapText + currentChunk;
            result.add(newChunk);
        }

        return result;
    }

    /**
     * 从文本末尾提取语义完整的重叠部分
     */
    private String extractOverlapText(String text, int overlapLength) {
        if (text.length() <= overlapLength) {
            return text;
        }

        // 从末尾开始，找到句子边界
        String tail = text.substring(text.length() - overlapLength);

        // 尝试在句子边界处切分
        int sentenceEnd = findSentenceBoundary(tail);
        if (sentenceEnd > 0) {
            return tail.substring(sentenceEnd);
        }

        // 如果找不到句子边界，在词边界处切分
        int wordEnd = findWordBoundary(tail);
        if (wordEnd > 0) {
            return tail.substring(wordEnd);
        }

        // 兜底：直接截取
        return tail;
    }

    /**
     * 查找句子边界
     */
    private int findSentenceBoundary(String text) {
        // 查找最后一个句子结束符
        String[] delimiters = {"。", "！", "？", "；", ".", "!", "?", ";"};
        int lastBoundary = -1;

        for (String delimiter : delimiters) {
            int index = text.lastIndexOf(delimiter);
            if (index > lastBoundary) {
                lastBoundary = index + delimiter.length();
            }
        }

        return lastBoundary;
    }

    /**
     * 查找词边界（使用HanLP）
     */
    private int findWordBoundary(String text) {
        List<Term> terms = StandardTokenizer.segment(text);

        int currentPos = 0;
        int lastWordEnd = 0;

        for (Term term : terms) {
            currentPos += term.word.length();
            if (currentPos <= text.length()) {
                lastWordEnd = currentPos;
            }
        }

        return lastWordEnd;
    }
}
