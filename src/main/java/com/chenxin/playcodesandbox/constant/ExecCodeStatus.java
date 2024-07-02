package com.chenxin.playcodesandbox.constant;

/**
 * @author fangchenxin
 * @description
 * @date 2024/7/2 20:33
 * @modify
 */
public enum ExecCodeStatus {
    SUCCESS(1, "通过"),
    EXCEEDS_LIMIT(2, "不满足限制"),
    ERROR(3, "错误");

    private Integer code;

    private String text;

    ExecCodeStatus(Integer code, String text) {
        this.code = code;
        this.text = text;
    }

    public Integer getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

}
