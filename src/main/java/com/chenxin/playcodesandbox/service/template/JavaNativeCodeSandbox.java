package com.chenxin.playcodesandbox.service.template;

import com.chenxin.playcodesandbox.model.ExecuteCodeRequest;
import com.chenxin.playcodesandbox.model.ExecuteCodeResponse;
import com.chenxin.playcodesandbox.service.template.JavaCodeSandboxTemplate;
import org.springframework.stereotype.Component;

/**
 * @author fangchenxin
 * @description Java原生实现
 * @date 2024/6/24 17:12
 * @modify
 */
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
