package com.rag;

import com.model.CourseResource;

import java.util.List;

/**
 * 候选片段的来源。
 *
 * <p>生产实现走 MySQL（{@code course_knowledge_chunk}）；评测实现走**内存语料快照**。
 * 这就是让评测摆脱数据库的那道缝——评测跑在舞台上、笔记本上时，
 * 不需要 MySQL、不需要 Docker、不需要 Spring 容器。
 */
public interface ChunkSource {

    /**
     * @param courseId  课程 ID
     * @param resources 该课程的资料列表，实现可据此决定是否重建索引
     * @return 候选片段，{@code score} 字段由检索策略填充，此处恒为 0
     */
    List<Chunk> load(Integer courseId, List<CourseResource> resources);
}
