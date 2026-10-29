package com.service;

import com.mapper.GamificationMapper;
import com.mapper.WrongQuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 积分 / 等级 / 徽章 / 打卡 业务层。
 * 各业务动作（提交作业、掌握错题、考试、打卡）通过本服务统一发放积分并触发徽章评定。
 */
@Service
public class GamificationService {

    private static final Logger logger = LoggerFactory.getLogger(GamificationService.class);

    @Autowired
    private GamificationMapper gamificationMapper;

    @Autowired
    private WrongQuestionMapper wrongQuestionMapper;

    /** 等级公式：每满100积分升一级。 */
    private int levelOf(int totalPoints) {
        return totalPoints / 100 + 1;
    }

    /**
     * 发放积分核心方法：累加积分 + 记录流水 + 重算等级 + 评定徽章。
     */
    public synchronized List<String> addPoints(Integer studentid, int delta, String type, String reason) {
        if (studentid == null || delta == 0) {
            return Collections.emptyList();
        }
        try {
            gamificationMapper.addPoints(studentid, delta);
            gamificationMapper.insertLog(studentid, delta, type, reason);
            Map<String, Object> p = gamificationMapper.getPoints(studentid);
            int total = p != null ? toInt(p.get("totalPoints")) : 0;
            gamificationMapper.updateLevel(studentid, levelOf(total));
            return evaluateBadges(studentid);
        } catch (Exception e) {
            logger.warn("发放积分失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void onHomeworkSubmit(Integer studentid) {
        addPoints(studentid, 5, "HOMEWORK", "提交作业 +5");
    }

    public void onMasterWrongQuestion(Integer studentid) {
        addPoints(studentid, 2, "MASTER", "攻克错题 +2");
    }

    /**
     * 考试交卷积分：按得分率折算（满分额外奖励 + 满分徽章）。
     */
    public void onExamSubmit(Integer studentid, double score, double fullMark) {
        int base = 10;
        int bonus = 0;
        if (fullMark > 0) {
            double rate = score / fullMark;
            bonus = (int) Math.round(rate * 20);
            if (rate >= 0.999) {
                bonus += 10;
                try {
                    gamificationMapper.awardBadge(studentid, "EXAM_FULL");
                } catch (Exception ignored) {
                }
            }
        }
        addPoints(studentid, base + bonus, "EXAM", "完成考试 +" + (base + bonus));
    }

    /**
     * 每日打卡：根据连续性给予递增奖励。
     */
    public synchronized Map<String, Object> checkin(Integer studentid) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> p = gamificationMapper.getPoints(studentid);
        LocalDate today = LocalDate.now();

        Object lastObj = p != null ? p.get("lastCheckin") : null;
        LocalDate last = parseDate(lastObj);
        int continuous = p != null ? toInt(p.get("continuousDays")) : 0;

        if (last != null && last.isEqual(today)) {
            result.put("already", true);
            result.put("continuousDays", continuous);
            result.put("awarded", 0);
            return result;
        }

        if (last != null && last.plusDays(1).isEqual(today)) {
            continuous = continuous + 1;
        } else {
            continuous = 1;
        }

        // 确保积分行存在
        if (p == null) {
            gamificationMapper.addPoints(studentid, 0);
        }
        gamificationMapper.updateCheckin(studentid, today.toString(), continuous);

        int award = Math.min(5 + (continuous - 1), 15); // 连续打卡递增，封顶15
        addPoints(studentid, award, "CHECKIN", "连续打卡第" + continuous + "天 +" + award);

        result.put("already", false);
        result.put("continuousDays", continuous);
        result.put("awarded", award);
        return result;
    }

    /**
     * 评定并补发达成条件的徽章，返回本次新获得的徽章编码。
     */
    public List<String> evaluateBadges(Integer studentid) {
        List<String> newly = new ArrayList<>();
        try {
            List<Map<String, Object>> defs = gamificationMapper.listBadgeDefs();
            Set<String> owned = new HashSet<>();
            for (Map<String, Object> b : gamificationMapper.listStudentBadges(studentid)) {
                owned.add(String.valueOf(b.get("badgeCode")));
            }
            Map<String, Object> p = gamificationMapper.getPoints(studentid);
            int total = p != null ? toInt(p.get("totalPoints")) : 0;
            int continuous = p != null ? toInt(p.get("continuousDays")) : 0;
            long homeworkCount = gamificationMapper.countHomeworkSubmissions(studentid);
            long masteredCount = wrongQuestionMapper.countMastered(studentid);

            for (Map<String, Object> def : defs) {
                String code = String.valueOf(def.get("code"));
                if (owned.contains(code)) {
                    continue;
                }
                String type = String.valueOf(def.get("conditionType"));
                int threshold = toInt(def.get("threshold"));
                boolean reached = false;
                switch (type) {
                    case "POINTS": reached = total >= threshold; break;
                    case "HOMEWORK": reached = homeworkCount >= threshold; break;
                    case "CHECKIN": reached = continuous >= threshold; break;
                    case "MASTER": reached = masteredCount >= threshold; break;
                    case "EXAM": /* 由考试满分直接发放 */ break;
                    default: break;
                }
                if (reached) {
                    gamificationMapper.awardBadge(studentid, code);
                    newly.add(code);
                }
            }
        } catch (Exception e) {
            logger.warn("评定徽章失败: {}", e.getMessage());
        }
        return newly;
    }

    /**
     * 学生积分画像：积分、等级、连续打卡、已获徽章、全部徽章、近期流水、全校排名。
     */
    public Map<String, Object> getProfile(Integer studentid) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> p = gamificationMapper.getPoints(studentid);
        int total = p != null ? toInt(p.get("totalPoints")) : 0;
        int continuous = p != null ? toInt(p.get("continuousDays")) : 0;
        LocalDate last = p != null ? parseDate(p.get("lastCheckin")) : null;

        data.put("totalPoints", total);
        data.put("level", levelOf(total));
        data.put("nextLevelGap", (levelOf(total)) * 100 - total);
        data.put("continuousDays", continuous);
        data.put("checkedToday", last != null && last.isEqual(LocalDate.now()));
        data.put("rank", gamificationMapper.rankOf(studentid));

        List<Map<String, Object>> defs = gamificationMapper.listBadgeDefs();
        Set<String> owned = new HashSet<>();
        Map<String, Object> ownedTime = new HashMap<>();
        for (Map<String, Object> b : gamificationMapper.listStudentBadges(studentid)) {
            String code = String.valueOf(b.get("badgeCode"));
            owned.add(code);
            ownedTime.put(code, b.get("obtainTime"));
        }
        for (Map<String, Object> def : defs) {
            String code = String.valueOf(def.get("code"));
            def.put("owned", owned.contains(code));
            def.put("obtainTime", ownedTime.get(code));
        }
        data.put("badges", defs);
        data.put("ownedBadgeCount", owned.size());
        data.put("recentLogs", gamificationMapper.recentLogs(studentid, 12));
        return data;
    }

    public List<Map<String, Object>> leaderboard(Integer classid, int limit) {
        return gamificationMapper.leaderboard(classid, limit);
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private LocalDate parseDate(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof java.sql.Date) {
                return ((java.sql.Date) o).toLocalDate();
            }
            if (o instanceof java.util.Date) {
                return new java.sql.Date(((java.util.Date) o).getTime()).toLocalDate();
            }
            String s = o.toString();
            if (s.length() >= 10) {
                return LocalDate.parse(s.substring(0, 10));
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
