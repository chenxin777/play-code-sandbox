package com.chenxin.playcodesandbox.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author fangchenxin
 * @description 判题信息
 * @date 2024/6/13 20:18
 * @modify
 */
@Data
public class JudgeInfo implements Serializable {

    private static final long serialVersionUID = 6555449204644728333L;
    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 程序执行时间ms
     */
    private Long time;

    /**
     * 运行消耗内存kb
     */
    private Long memory;
}
