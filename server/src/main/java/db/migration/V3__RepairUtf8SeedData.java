package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * 修复历史版本在 Windows 默认字符集下读取 UTF-8 SQL 导致的中文种子数据乱码。
 */
public class V3__RepairUtf8SeedData extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        repairAcademicTermNames(connection);
        repairBadgeDefinitions(connection);
    }

    private void repairAcademicTermNames(Connection connection) throws Exception {
        String sql = """
                UPDATE academic_term
                SET term_name = CONCAT(
                    SUBSTRING_INDEX(term_code, '-', 2),
                    '学年第',
                    SUBSTRING_INDEX(term_code, '-', -1),
                    '学期'
                )
                WHERE term_code REGEXP '^[0-9]{4}-[0-9]{4}-[1-3]$'
                  AND (
                    term_name LIKE '%瀛﹀勾%'
                    OR term_name LIKE '%绗%'
                    OR term_name LIKE '%�%'
                    OR TRIM(term_name) = ''
                  )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private void repairBadgeDefinitions(Connection connection) throws Exception {
        String sql = """
                INSERT INTO badge_def
                    (code, name, description, icon, condition_type, threshold)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    description = VALUES(description),
                    icon = VALUES(icon),
                    condition_type = VALUES(condition_type),
                    threshold = VALUES(threshold)
                """;
        Object[][] badges = {
                {"FIRST_SUBMIT", "初露锋芒", "完成第一次作业提交", "ph:medal-duotone", "HOMEWORK", 1},
                {"HOMEWORK_10", "勤学不辍", "累计提交10次作业", "ph:books-duotone", "HOMEWORK", 10},
                {"POINTS_100", "积分新星", "累计积分达到100", "ph:star-duotone", "POINTS", 100},
                {"POINTS_500", "积分达人", "累计积分达到500", "ph:sparkle-duotone", "POINTS", 500},
                {"CHECKIN_7", "坚持一周", "连续打卡7天", "ph:fire-duotone", "CHECKIN", 7},
                {"CHECKIN_30", "月度学霸", "连续打卡30天", "ph:crown-duotone", "CHECKIN", 30},
                {"EXAM_FULL", "满分王者", "单场考试获得满分", "ph:trophy-duotone", "EXAM", 100},
                {"MASTER_20", "纠错能手", "错题本掌握20题", "ph:target-duotone", "MASTER", 20}
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Object[] badge : badges) {
                for (int index = 0; index < badge.length; index++) {
                    statement.setObject(index + 1, badge[index]);
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
