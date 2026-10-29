package com.mapper;

import com.model.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 站内消息数据访问接口
 */
@Mapper
public interface MessageMapper {

    // 发送消息
    int insert(Message message);

    int batchInsert(@Param("list") List<Message> list);

    // 查询收到的消息
    List<Message> findReceived(@Param("receivertype") Integer receivertype,
                               @Param("receiverid") Integer receiverid,
                               @Param("isread") Integer isread,
                               @Param("offset") Integer offset,
                               @Param("limit") Integer limit);

    Long countReceived(@Param("receivertype") Integer receivertype,
                       @Param("receiverid") Integer receiverid,
                       @Param("isread") Integer isread);

    // 查询发送的消息
    List<Message> findSent(@Param("sendertype") Integer sendertype,
                           @Param("senderid") Integer senderid,
                           @Param("offset") Integer offset,
                           @Param("limit") Integer limit);

    Long countSent(@Param("sendertype") Integer sendertype,
                   @Param("senderid") Integer senderid);

    Message findById(@Param("id") Integer id);

    // 标记已读
    int markAsRead(@Param("id") Integer id);

    int markAllAsRead(@Param("receivertype") Integer receivertype,
                      @Param("receiverid") Integer receiverid);

    int delete(@Param("id") Integer id);

    // 未读消息数
    Long countUnread(@Param("receivertype") Integer receivertype,
                     @Param("receiverid") Integer receiverid);
}
