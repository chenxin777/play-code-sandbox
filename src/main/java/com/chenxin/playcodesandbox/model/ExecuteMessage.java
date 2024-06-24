package com.chenxin.playcodesandbox.model;

import lombok.Data;

/**
 * @author fangchenxin
 * @description
 * @date 2024/6/18 18:16
 * @modify
 */
@Data
public class ExecuteMessage {

    private Integer exitValue;

    private String message;

    private String errorMessage;

    private Long time;

    private Long memory;

}
