package com.response;

/**
 * 统一响应结果类
 * 用于封装API接口的返回数据
 * 
 * @param <T> 数据类型
 */
public class Result<T> {

    /**
     * 状态码：200表示成功，其他表示失败
     */
    private Integer code;

    /**
     * 提示信息
     */
    private String msg;

    /**
     * 返回数据
     */
    private T data;

    /**
     * 总记录数（分页查询时使用）
     */
    private Long total;

    /**
     * 默认构造函数
     */
    public Result() {
    }

    /**
     * 带参数构造函数
     * 
     * @param code 状态码
     * @param msg  提示信息
     * @param data 数据
     */
    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功响应（无数据）
     * 
     * @return Result对象
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /**
     * 成功响应（带数据）
     * 
     * @param data 返回数据
     * @return Result对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功响应（只带消息，无数据）
     * 
     * @param msg 提示信息
     * @return Result对象
     */
    public static Result<Void> successMsg(String msg) {
        return new Result<>(200, msg, null);
    }

    /**
     * 成功响应（带消息和数据）
     * 
     * @param msg  提示信息
     * @param data 返回数据
     * @return Result对象
     */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    /**
     * 分页查询成功响应
     * 
     * @param data  数据列表
     * @param total 总记录数
     * @return Result对象
     */
    public static <T> Result<T> success(T data, Long total) {
        Result<T> result = new Result<>(200, "查询成功", data);
        result.setTotal(total);
        return result;
    }

    /**
     * 失败响应
     * 
     * @param msg 错误信息
     * @return Result对象
     */
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    /**
     * 失败响应（带状态码）
     * 
     * @param code 状态码
     * @param msg  错误信息
     * @return Result对象
     */
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }

    // Getter和Setter方法

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
