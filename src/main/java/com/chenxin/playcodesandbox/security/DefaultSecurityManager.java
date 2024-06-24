package com.chenxin.playcodesandbox.security;

import java.security.Permission;

/**
 * @author fangchenxin
 * @description
 * @date 2024/6/19 20:48
 * @modify
 */
public class DefaultSecurityManager extends SecurityManager{
    @Override
    public void checkPermission(Permission perm) {
        //super.checkPermission(perm);
    }
}
