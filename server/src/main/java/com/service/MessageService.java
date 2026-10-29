package com.service;

import com.config.NotifyWebSocketHandler;
import com.mapper.MessageMapper;
import com.mapper.StudentMapper;
import com.model.Message;
import com.model.Student;
import com.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 站内消息业务逻辑层
 */
@Service
public class MessageService {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private NotifyWebSocketHandler notifyWebSocketHandler;

    /**
     * 发送消息给个人
     */
    public Result<Void> send(Message message) {
        if (message.getTitle() == null || message.getTitle().trim().isEmpty()) {
            return Result.error("标题不能为空");
        }
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            return Result.error("内容不能为空");
        }
        if (message.getSendertype() == null || message.getSenderid() == null) {
            return Result.error("发送者信息不完整");
        }
        if (message.getReceivertype() == null || message.getReceiverid() == null) {
            return Result.error("接收者信息不完整");
        }

        message.setTitle(message.getTitle().trim());
        message.setContent(message.getContent().trim());
        message.setCreatetime(new Date());

        int rows = messageMapper.insert(message);
        if (rows > 0) {
            // 实时推送给接收者（在线时弹窗/红点）
            notifyWebSocketHandler.pushMessage(message.getReceivertype(), message.getReceiverid(),
                    message.getTitle(), message.getContent());
            return Result.success();
        }
        return Result.error("发送失败");
    }

    /**
     * 发送消息给班级（给班级内所有学生发送）
     */
    @Transactional
    public Result<Void> sendToClass(String title, String content, Integer sendertype, 
                                     Integer senderid, Integer classid) {
        if (title == null || title.trim().isEmpty()) {
            return Result.error("标题不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            return Result.error("内容不能为空");
        }

        // 获取班级内所有学生
        List<Student> students = studentMapper.findByClassid(classid);
        if (students == null || students.isEmpty()) {
            return Result.error("班级内没有学生");
        }

        Date now = new Date();
        List<Message> messages = new ArrayList<>();
        for (Student student : students) {
            Message message = new Message();
            message.setTitle(title.trim());
            message.setContent(content.trim());
            message.setSendertype(sendertype);
            message.setSenderid(senderid);
            message.setReceivertype(1); // 学生
            message.setReceiverid(student.getStudentid());
            message.setCreatetime(now);
            messages.add(message);
        }

        int rows = messageMapper.batchInsert(messages);
        if (rows > 0) {
            // 逐个推送给班级内在线学生
            for (Student student : students) {
                notifyWebSocketHandler.pushMessage(1, student.getStudentid(), title.trim(), content.trim());
            }
            return Result.success();
        }
        return Result.error("发送失败");
    }

    /**
     * 异步给班级群发消息（用于签到提醒等场景，不阻塞主业务流程）
     */
    @Async
    public void sendToClassAsync(String title, String content, Integer sendertype,
                                 Integer senderid, Integer classid) {
        try {
            sendToClass(title, content, sendertype, senderid, classid);
        } catch (Exception ignored) {
            // 提醒发送失败不影响主流程
        }
    }

    /**
     * 获取收到的消息
     */
    public List<Message> getReceived(Integer receivertype, Integer receiverid, Integer isread,
                                      Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return messageMapper.findReceived(receivertype, receiverid, isread, offset, pageSize);
    }

    public Long getReceivedCount(Integer receivertype, Integer receiverid, Integer isread) {
        return messageMapper.countReceived(receivertype, receiverid, isread);
    }

    /**
     * 获取发送的消息
     */
    public List<Message> getSent(Integer sendertype, Integer senderid, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return messageMapper.findSent(sendertype, senderid, offset, pageSize);
    }

    public Long getSentCount(Integer sendertype, Integer senderid) {
        return messageMapper.countSent(sendertype, senderid);
    }

    /**
     * 获取消息详情
     */
    public Message getById(Integer id) {
        return messageMapper.findById(id);
    }

    /**
     * 标记消息已读
     */
    public Result<Void> markAsRead(Integer id) {
        if (id == null) {
            return Result.error("消息ID不能为空");
        }

        Message message = messageMapper.findById(id);
        if (message == null) {
            return Result.error("消息不存在");
        }

        int rows = messageMapper.markAsRead(id);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("标记失败");
    }

    /**
     * 标记所有消息已读
     */
    public Result<Void> markAllAsRead(Integer receivertype, Integer receiverid) {
        if (receivertype == null || receiverid == null) {
            return Result.error("参数不完整");
        }

        messageMapper.markAllAsRead(receivertype, receiverid);
        return Result.success();
    }

    /**
     * 删除消息
     */
    public Result<Void> delete(Integer id) {
        if (id == null) {
            return Result.error("消息ID不能为空");
        }

        Message message = messageMapper.findById(id);
        if (message == null) {
            return Result.error("消息不存在");
        }

        int rows = messageMapper.delete(id);
        if (rows > 0) {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    /**
     * 获取未读消息数
     */
    public Long getUnreadCount(Integer receivertype, Integer receiverid) {
        Long count = messageMapper.countUnread(receivertype, receiverid);
        return count != null ? count : 0L;
    }
}
