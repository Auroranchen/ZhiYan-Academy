package com.service;

import com.mapper.PeerReviewMapper;
import com.response.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 作业互评业务层。
 * 教师对某次作业开启互评后，系统在已提交学生之间循环分配评阅任务；
 * 学生完成评阅可获得积分，教师可查看每位学生收到的互评均分与评语。
 * 表结构通过 JdbcTemplate 在启动时自动创建。
 */
@Service
public class PeerReviewService {

    private static final Logger logger = LoggerFactory.getLogger(PeerReviewService.class);

    @Autowired
    private PeerReviewMapper peerReviewMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GamificationService gamificationService;

    /**
     * 开启 / 重新分配互评。在已提交学生之间循环分配评阅任务。
     */
    public Result<Map<String, Object>> assign(Integer homeworkid, Integer teacherid,
                                              Integer reviewersPer, Integer maxScore, Integer anonymous) {
        if (homeworkid == null) {
            return Result.error("作业ID不能为空");
        }
        int per = reviewersPer != null && reviewersPer > 0 ? reviewersPer : 2;
        int max = maxScore != null && maxScore > 0 ? maxScore : 100;
        int anon = (anonymous != null && anonymous == 0) ? 0 : 1;

        List<Map<String, Object>> subs = peerReviewMapper.listSubmissions(homeworkid);
        if (subs == null || subs.size() < 2) {
            return Result.error("已提交人数不足 2 人，暂时无法开展互评");
        }
        int n = subs.size();
        if (per > n - 1) {
            per = n - 1; // 评阅人数不能超过其他提交人数
        }

        try {
            peerReviewMapper.upsertTask(homeworkid, teacherid, per, max, anon);
            peerReviewMapper.deleteAssignments(homeworkid);

            int created = 0;
            for (int i = 0; i < n; i++) {
                Integer reviewerId = toInt(subs.get(i).get("studentid"));
                for (int k = 1; k <= per; k++) {
                    Map<String, Object> target = subs.get((i + k) % n);
                    Integer submitId = toInt(target.get("submitid"));
                    Integer revieweeId = toInt(target.get("studentid"));
                    if (reviewerId == null || submitId == null || revieweeId == null) continue;
                    if (reviewerId.equals(revieweeId)) continue;
                    created += peerReviewMapper.insertAssignment(homeworkid, reviewerId, submitId, revieweeId);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("submitters", n);
            data.put("reviewersPer", per);
            data.put("maxScore", max);
            data.put("anonymous", anon);
            data.put("assignments", created);
            return Result.success("已为 " + n + " 名学生分配互评任务", data);
        } catch (Exception e) {
            logger.error("分配互评任务失败: {}", e.getMessage(), e);
            return Result.error("分配失败，请稍后重试");
        }
    }

    public Map<String, Object> getTask(Integer homeworkid) {
        Map<String, Object> task = peerReviewMapper.getTask(homeworkid);
        Map<String, Object> result = new HashMap<>();
        result.put("task", task);
        result.put("progress", peerReviewMapper.progress(homeworkid));
        return result;
    }

    public Result<Void> closeTask(Integer homeworkid) {
        peerReviewMapper.updateTaskStatus(homeworkid, 0);
        return Result.success();
    }

    public List<Map<String, Object>> summary(Integer homeworkid) {
        return peerReviewMapper.summaryByHomework(homeworkid);
    }

    /**
     * 将互评同伴均分一键回填为学生作业成绩（参考成绩）。
     * 仅处理已有有效均分的学生，写入分数、评语并置为已批改。
     */
    public Result<Map<String, Object>> applyScores(Integer homeworkid) {
        if (homeworkid == null) {
            return Result.error("作业ID不能为空");
        }
        List<Map<String, Object>> rows = peerReviewMapper.summaryByHomework(homeworkid);
        int updated = 0;
        for (Map<String, Object> row : rows) {
            Object avg = row.get("avgScore");
            Integer revieweeId = toInt(row.get("revieweeid"));
            if (avg == null || revieweeId == null) continue;
            try {
                BigDecimal score = new BigDecimal(avg.toString());
                int n = jdbcTemplate.update(
                    "UPDATE studenthomework SET score = ?, comment = ?, status = 2, gradetime = NOW() " +
                    "WHERE homeworkid = ? AND studentid = ?",
                    score, "互评参考成绩（同伴均分 " + avg + "）", homeworkid, revieweeId);
                updated += n;
            } catch (Exception e) {
                logger.warn("回填互评成绩失败 student={}: {}", revieweeId, e.getMessage());
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("updated", updated);
        return Result.success("已回填 " + updated + " 名学生的参考成绩", data);
    }

    public List<Map<String, Object>> myTasks(Integer studentid, Integer status) {
        return peerReviewMapper.listByReviewer(studentid, status);
    }

    public List<Map<String, Object>> received(Integer studentid) {
        return peerReviewMapper.listReceived(studentid);
    }

    /**
     * 评阅详情。仅评阅人本人可见；匿名作业隐去被评者姓名。
     */
    public Result<Map<String, Object>> detail(Integer id, Integer studentid) {
        Map<String, Object> r = peerReviewMapper.getReview(id);
        if (r == null) {
            return Result.error("互评任务不存在");
        }
        if (!toInt(r.get("reviewerid")).equals(studentid)) {
            return Result.error("无权查看该互评任务");
        }
        if (toInt(r.get("anonymous")) == 1) {
            r.put("revieweename", "匿名同学");
        }
        return Result.success(r);
    }

    /**
     * 提交互评。校验分数范围与归属，首次完成发放积分。
     */
    public Result<Void> submit(Integer id, Integer studentid, BigDecimal score, String comment) {
        if (id == null || studentid == null) {
            return Result.error("参数不完整");
        }
        if (score == null) {
            return Result.error("请打分");
        }
        Map<String, Object> r = peerReviewMapper.getReview(id);
        if (r == null) {
            return Result.error("互评任务不存在");
        }
        if (!toInt(r.get("reviewerid")).equals(studentid)) {
            return Result.error("无权评阅该作业");
        }
        Integer reviewStatus = toInt(r.get("status"));
        if (reviewStatus != null && reviewStatus != 0) {
            return Result.error("已提交，不可修改");
        }
        Integer taskStatus = toInt(r.get("taskStatus"));
        if (taskStatus != null && taskStatus != 1) {
            return Result.error("互评已关闭，无法提交");
        }
        int max = toInt(r.get("maxScore"));
        if (max <= 0) max = 100;
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal(max)) > 0) {
            return Result.error("分数需在 0 ~ " + max + " 之间");
        }
        boolean firstTime = reviewStatus == null || reviewStatus == 0;
        int rows = peerReviewMapper.submitReview(id, studentid, score, comment);
        if (rows <= 0) {
            return Result.error("提交失败，可能已提交或互评已关闭");
        }
        if (firstTime) {
            try {
                gamificationService.addPoints(studentid, 3, "PEER", "完成作业互评 +3");
            } catch (Exception ignored) {
            }
        }
        return Result.success();
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
