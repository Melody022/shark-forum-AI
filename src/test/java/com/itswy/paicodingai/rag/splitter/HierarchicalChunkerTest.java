package com.itswy.paicodingai.rag.splitter;

import com.itswy.paicodingai.file.parser.BlockType;
import com.itswy.paicodingai.file.parser.ChunkDraft;
import com.itswy.paicodingai.file.parser.ContentBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchicalChunkerTest {

    private final HierarchicalChunker chunker = new HierarchicalChunker();

    @Test
    void createsParentAndChildChunksWithSectionMetadata() {
        List<ContentBlock> blocks = List.of(
                ContentBlock.builder().blockId("h1").type(BlockType.HEADING).content("自动配置")
                        .sectionPath("第3章 > 自动配置").parentId("section-1").searchable(false).build(),
                ContentBlock.builder().blockId("text-1").type(BlockType.TEXT).content("自动配置根据 classpath 条件加载 Bean。")
                        .sectionPath("第3章 > 自动配置").parentId("section-1").build(),
                ContentBlock.builder().blockId("code-1").type(BlockType.CODE).content("@EnableAutoConfiguration")
                        .sectionPath("第3章 > 自动配置").parentId("section-1").build());

        List<ChunkDraft> drafts = chunker.chunk(blocks);

        assertEquals(1, drafts.stream().filter(draft -> ChunkDraft.ROLE_PARENT.equals(draft.getRole())).count());
        assertEquals(2, drafts.stream().filter(draft -> ChunkDraft.ROLE_CHILD.equals(draft.getRole())).count());
        assertTrue(drafts.stream().filter(draft -> ChunkDraft.ROLE_CHILD.equals(draft.getRole()))
                .allMatch(draft -> draft.getContent().contains("第3章 > 自动配置")));
        assertFalse(drafts.stream().filter(draft -> ChunkDraft.ROLE_CHILD.equals(draft.getRole()))
                .anyMatch(draft -> draft.getBlockType() == BlockType.HEADING));
    }

    @Test
    void repeatsTableHeaderWhenLargeTableIsSplit() {
        StringBuilder table = new StringBuilder("工作表：版本\n表头：版本 | 特性\n字段：版本 | 特性\n");
        for (int i = 0; i < 160; i++) {
            table.append("行 ").append(i).append("：").append(i).append(".0 | feature-").append(i).append("\n");
        }
        ContentBlock block = ContentBlock.builder().blockId("table-1").type(BlockType.TABLE)
                .content(table.toString()).parentId("root").sectionPath("版本表").build();

        List<ChunkDraft> children = chunker.chunk(List.of(block)).stream()
                .filter(draft -> ChunkDraft.ROLE_CHILD.equals(draft.getRole()))
                .toList();

        assertTrue(children.size() > 1);
        assertTrue(children.stream().allMatch(child -> child.getContent().contains("表头：版本 | 特性")));
    }
}
