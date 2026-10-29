package com.service;

import com.model.CourseResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class CourseKnowledgeContextServiceTest {

    @TempDir
    Path tempDir;

    private CourseKnowledgeContextService service;
    private CourseResourceService resourceService;

    @BeforeEach
    void setUp() {
        service = new CourseKnowledgeContextService();
        resourceService = Mockito.mock(CourseResourceService.class);
        ReflectionTestUtils.setField(service, "courseResourceService", resourceService);
        ReflectionTestUtils.setField(service, "wordService", new WordService());
        ReflectionTestUtils.setField(service, "embeddingService", new LocalEmbeddingService());
    }

    private CourseResource resource(int id, String title, String filename, Path path) {
        CourseResource resource = new CourseResource();
        resource.setId(id);
        resource.setTitle(title);
        resource.setFilename(filename);
        resource.setFilepath(path.toString());
        return resource;
    }

    @Test
    void nullCourseIdReturnsEmptyContext() {
        CourseKnowledgeContextService.KnowledgeContext context = service.build(null);
        assertEquals(0, context.sourceCount());
        assertEquals("", context.text());
    }

    @Test
    void buildsContextAndValidatesCitationFormats() throws Exception {
        Path file = tempDir.resolve("intro.md");
        Files.writeString(file, "# 第1章 绪论\n人工智能是研究智能行为的学科。", StandardCharsets.UTF_8);
        when(resourceService.getByCourseid(eq(7), anyInt(), anyInt()))
                .thenReturn(List.of(resource(1, "绪论讲义", "intro.md", file)));
        when(resourceService.resolveStoredPath(any())).thenAnswer(inv -> Path.of(inv.getArgument(0, String.class)));

        CourseKnowledgeContextService.KnowledgeContext context = service.build(7, "人工智能");

        assertEquals(1, context.sourceCount());
        assertTrue(context.text().contains("人工智能是研究智能行为的学科"));
        assertEquals(true, service.validateCitations("结论。[资料1-1]", context).get("valid"));
        assertEquals(true, service.validateCitations("结论【资料1-1｜绪论讲义｜片段1】", context).get("valid"));
        assertEquals(false, service.validateCitations("错误。[资料999-1]", context).get("valid"));
        assertEquals(true, service.validateCitations("结构化元数据", context, false).get("valid"));
    }

    @Test
    void truncatesOversizedResource() throws Exception {
        Path file = tempDir.resolve("big.txt");
        Files.writeString(file, "知识点。".repeat(5000), StandardCharsets.UTF_8);
        when(resourceService.getByCourseid(eq(8), anyInt(), anyInt()))
                .thenReturn(List.of(resource(1, "超长资料", "big.txt", file)));
        when(resourceService.resolveStoredPath(any())).thenAnswer(inv -> Path.of(inv.getArgument(0, String.class)));

        CourseKnowledgeContextService.KnowledgeContext context = service.build(8);
        assertEquals(1, context.sourceCount());
        assertTrue(context.text().length() < 7000);
    }

    @Test
    void unreadableFileFallsBackToDescription() {
        CourseResource resource = resource(1, "已删除的课件", "gone.pdf", tempDir.resolve("gone.pdf"));
        resource.setDescription("本课件介绍神经网络基础");
        when(resourceService.getByCourseid(eq(9), anyInt(), anyInt())).thenReturn(List.of(resource));
        when(resourceService.resolveStoredPath(any())).thenAnswer(inv -> Path.of(inv.getArgument(0, String.class)));

        CourseKnowledgeContextService.KnowledgeContext context = service.build(9);
        assertEquals(1, context.sourceCount());
        assertTrue(context.text().contains("本课件介绍神经网络基础"));
    }

    @Test
    void resourceListFailureReturnsEmpty() {
        when(resourceService.getByCourseid(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("db down"));
        assertEquals(0, service.build(10).sourceCount());
    }

    @Test
    void vectorQueryRanksRelevantChunkFirst() throws Exception {
        Path tree = tempDir.resolve("tree.md");
        Path database = tempDir.resolve("db.md");
        Files.writeString(tree, "二叉树前序遍历遵循根节点、左子树、右子树的访问顺序。", StandardCharsets.UTF_8);
        Files.writeString(database, "数据库事务具有原子性、一致性、隔离性和持久性。", StandardCharsets.UTF_8);
        when(resourceService.getByCourseid(eq(11), anyInt(), anyInt()))
                .thenReturn(List.of(
                        resource(2, "数据库", "db.md", database),
                        resource(1, "树结构", "tree.md", tree)));
        when(resourceService.resolveStoredPath(any())).thenAnswer(inv -> Path.of(inv.getArgument(0, String.class)));

        CourseKnowledgeContextService.KnowledgeContext context = service.build(11, "二叉树前序遍历");
        assertFalse(context.citations().isEmpty());
        assertEquals("树结构", context.citations().get(0).title());
        assertEquals(1, context.citations().size(), "低相关数据库片段不应被注入模型上下文");
    }

    @Test
    void hybridRankingUsesTitleAndExactTerms() throws Exception {
        Path inheritance = tempDir.resolve("inheritance.md");
        Path generic = tempDir.resolve("generic.md");
        Files.writeString(inheritance, "子类通过 extends 关键字继承父类，并可以重写父类方法。", StandardCharsets.UTF_8);
        Files.writeString(generic, "Java 程序包含类、对象、方法和变量等基础元素。", StandardCharsets.UTF_8);
        when(resourceService.getByCourseid(eq(12), anyInt(), anyInt()))
                .thenReturn(List.of(
                        resource(2, "Java基础", "generic.md", generic),
                        resource(1, "继承与方法重写", "inheritance.md", inheritance)));
        when(resourceService.resolveStoredPath(any())).thenAnswer(inv -> Path.of(inv.getArgument(0, String.class)));

        CourseKnowledgeContextService.KnowledgeContext context = service.build(12, "extends 继承");

        assertFalse(context.citations().isEmpty());
        assertEquals("继承与方法重写", context.citations().get(0).title());
    }

    @Test
    void unrelatedQueryDoesNotInjectCourseMaterial() throws Exception {
        Path file = tempDir.resolve("database.md");
        Files.writeString(file, "数据库事务具有原子性、一致性、隔离性和持久性。", StandardCharsets.UTF_8);
        when(resourceService.getByCourseid(eq(13), anyInt(), anyInt()))
                .thenReturn(List.of(resource(1, "数据库", "database.md", file)));
        when(resourceService.resolveStoredPath(any())).thenAnswer(inv -> Path.of(inv.getArgument(0, String.class)));

        CourseKnowledgeContextService.KnowledgeContext context = service.build(13, "唐代诗歌格律");

        assertTrue(context.citations().isEmpty());
        assertEquals("", context.text());
    }
}
