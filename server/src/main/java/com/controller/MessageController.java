package com.controller;

import com.config.RequireRole;
import com.model.Message;
import com.response.Result;
import com.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;

/**
 * 站内消息控制器
 */
@RestController
@RequestMapping("/message")
public class MessageController {

    @Autowired
    private MessageService messageService;

    /**
     * 发送消息给个人
     */
    @PostMapping("/send")
    @RequireRole({"admin", "teacher"})
    public Result<Void> send(@RequestBody Message message, HttpSession session) {
        applySender(message, session);
        return messageService.send(message);
    }

    /**
     * 发送消息给班级
     */
    @PostMapping("/sendToClass")
    @RequireRole({"admin", "teacher"})
    public Result<Void> sendToClass(@RequestBody Map<String, Object> params, HttpSession session) {
        String title = (String) params.get("title");
        String content = (String) params.get("content");
        Integer classid = (Integer) params.get("classid");
        return messageService.sendToClass(title, content, senderType(session), senderId(session), classid);
    }

    /**
     * 获取收到的消息
     */
    @GetMapping("/received")
    public Result<List<Message>> getReceived(
            @RequestParam Integer receivertype,
            @RequestParam Integer receiverid,
            @RequestParam(required = false) Integer isread,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        Integer actualType = receiverType(session);
        Integer actualId = receiverId(session);
        if (actualType == null || actualId == null) return Result.error(403, "当前角色没有个人收件箱");
        List<Message> list = messageService.getReceived(actualType, actualId, isread, page, pageSize);
        Long total = messageService.getReceivedCount(actualType, actualId, isread);
        return Result.success(list, total);
    }

    /**
     * 获取发送的消息
     */
    @GetMapping("/sent")
    public Result<List<Message>> getSent(
            @RequestParam Integer sendertype,
            @RequestParam Integer senderid,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        Integer actualType = senderType(session);
        Integer actualId = senderId(session);
        if (actualType == null || actualId == null) return Result.error(403, "当前角色不能发送消息");
        List<Message> list = messageService.getSent(actualType, actualId, page, pageSize);
        Long total = messageService.getSentCount(actualType, actualId);
        return Result.success(list, total);
    }

    /**
     * 获取消息详情
     */
    @GetMapping("/{id}")
    public Result<Message> getById(@PathVariable("id") Integer id, HttpSession session) {
        Message message = messageService.getById(id);
        if (message == null) {
            return Result.error("消息不存在");
        }
        return canView(message, session) ? Result.success(message) : Result.error(403, "无权查看该消息");
    }

    /**
     * 标记消息已读
     */
    @PutMapping("/read/{id}")
    public Result<Void> markAsRead(@PathVariable("id") Integer id, HttpSession session) {
        Message message = messageService.getById(id);
        if (message == null) return Result.error("消息不存在");
        if (!isReceiver(message, session)) return Result.error(403, "无权操作该消息");
        return messageService.markAsRead(id);
    }

    /**
     * 标记所有消息已读
     */
    @PutMapping("/readAll")
    public Result<Void> markAllAsRead(@RequestBody Map<String, Integer> params, HttpSession session) {
        Integer receivertype = receiverType(session);
        Integer receiverid = receiverId(session);
        if (receivertype == null || receiverid == null) return Result.error(403, "当前角色没有个人收件箱");
        return messageService.markAllAsRead(receivertype, receiverid);
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id, HttpSession session) {
        Message message = messageService.getById(id);
        if (message == null) return Result.error("消息不存在");
        if (!isSender(message, session)) return Result.error(403, "仅发送者可以删除消息");
        return messageService.delete(id);
    }

    /**
     * 获取未读消息数
     */
    @GetMapping("/unreadCount")
    public Result<Long> getUnreadCount(
            @RequestParam Integer receivertype,
            @RequestParam Integer receiverid,
            HttpSession session) {
        Integer actualType = receiverType(session);
        Integer actualId = receiverId(session);
        if (actualType == null || actualId == null) return Result.error(403, "当前角色没有个人收件箱");
        Long count = messageService.getUnreadCount(actualType, actualId);
        return Result.success(count);
    }

    private void applySender(Message message, HttpSession session) {
        message.setSendertype(senderType(session));
        message.setSenderid(senderId(session));
    }

    private Integer senderType(HttpSession session) {
        if (session.getAttribute("adminid") != null) return 1;
        if (session.getAttribute("teacherid") != null) return 2;
        return null;
    }

    private Integer senderId(HttpSession session) {
        if (session.getAttribute("adminid") != null) return (Integer) session.getAttribute("adminid");
        return (Integer) session.getAttribute("teacherid");
    }

    private Integer receiverType(HttpSession session) {
        if (session.getAttribute("studentid") != null) return 1;
        if (session.getAttribute("teacherid") != null) return 2;
        return null;
    }

    private Integer receiverId(HttpSession session) {
        if (session.getAttribute("studentid") != null) return (Integer) session.getAttribute("studentid");
        return (Integer) session.getAttribute("teacherid");
    }

    private boolean isSender(Message message, HttpSession session) {
        Integer type = senderType(session);
        Integer id = senderId(session);
        return type != null && id != null
                && type.equals(message.getSendertype()) && id.equals(message.getSenderid());
    }

    private boolean isReceiver(Message message, HttpSession session) {
        Integer type = receiverType(session);
        Integer id = receiverId(session);
        return type != null && id != null
                && type.equals(message.getReceivertype()) && id.equals(message.getReceiverid());
    }

    private boolean canView(Message message, HttpSession session) {
        return isSender(message, session) || isReceiver(message, session);
    }
}
