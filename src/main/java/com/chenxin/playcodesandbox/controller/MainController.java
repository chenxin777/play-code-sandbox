package com.chenxin.playcodesandbox.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author fangchenxin
 * @description
 * @date 2024/6/18 11:47
 * @modify
 */
@RestController
@RequestMapping("/")
public class MainController {

    @GetMapping("/health")
    public String healthCheck() {
        return "OK";
    }

}
