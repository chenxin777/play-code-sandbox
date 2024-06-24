package com.chenxin.playcodesandbox.unsafe;

import java.util.ArrayList;
import java.util.List;

/**
 * @author fangchenxin
 * @description 无限占用空间
 * @date 2024/6/19 11:17
 * @modify
 */
public class MemoryError {

    public static void main(String[] args) throws InterruptedException {
        List<byte[]> bytes = new ArrayList<>();
        while (true) {
            bytes.add(new byte[10000]);
        }
    }
}
