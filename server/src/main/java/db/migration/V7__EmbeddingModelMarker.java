package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 给课程知识切片加「向量器标记」列。
 *
 * <p><b>为什么必须有这两列：</b>{@code course_knowledge_chunk} 的失效判断原先只看
 * 内容指纹。换了向量器但文件没变时，指纹不变 → 短路命中 → 旧的、维度已不匹配的向量被沿用 →
 * 读取时逐行被跳过 → 候片为空 → 回落到「即时切片」。
 * 结果是<b>每次请求都要重新解析文件并重新向量化</b>。
 *
 * <p>这个故障不报错、不留日志，只表现为系统莫名变慢——属于最难查的一类。
 * 加上标记列后，向量器一变就会触发重建。
 *
 * <p>本迁移**不清空既有行**：标记为 NULL 就足以让失效判断不命中，从而自然触发重建。
 * 清空是破坏性的，而且没必要——重建本来就带 DELETE。
 *
 * <p>注意用 Java 迁移而非 SQL：MySQL 8 没有 {@code ADD COLUMN IF NOT EXISTS}，
 * 迁移必须幂等（空库与存量库都要能跑）。写法沿用 V2 的模板。
 */
public class V7__EmbeddingModelMarker extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, "course_knowledge_chunk")) {
            // 表由 V1 的 learning_effect_rag.sql 建立；这里只是防御性跳过
            return;
        }
        addColumnIfMissing(connection, "course_knowledge_chunk", "embedding_model",
                "VARCHAR(64) NULL COMMENT '生成该向量的模型标识，用于识别失效的旧向量'");
        addColumnIfMissing(connection, "course_knowledge_chunk", "embedding_dim",
                "INT NULL COMMENT '向量维度，与模型标识共同构成失效键'");
    }

    private void addColumnIfMissing(Connection connection, String table, String column,
                                    String definition) throws Exception {
        if (columnExists(connection, table, column)) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }
}
