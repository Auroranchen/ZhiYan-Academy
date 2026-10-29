package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 将学习路径任务进度列纳入正式迁移，避免业务 Service 在应用启动时修改表结构。
 */
public class V2__LearningPlanProgressColumn extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!columnExists(connection, "learning_plan", "progress_json")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE learning_plan ADD COLUMN progress_json MEDIUMTEXT");
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
