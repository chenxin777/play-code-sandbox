package com.chenxin.playcodesandbox.service.template;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.chenxin.playcodesandbox.model.ExecuteCodeRequest;
import com.chenxin.playcodesandbox.model.ExecuteCodeResponse;
import com.chenxin.playcodesandbox.model.ExecuteMessage;
import com.chenxin.playcodesandbox.model.JudgeInfo;
import com.chenxin.playcodesandbox.service.CodeSandbox;
import com.chenxin.playcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author fangchenxin
 * @description
 * @date 2024/6/24 10:21
 * @modify
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final long TIME_OUT = 5000L;

    /**
     * @param code 用户代码
     * @return java.io.File
     * @description 用户代码保存为文件
     * @author fangchenxin
     * @date 2024/6/24 10:37
     */
    public File saveCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码路径是否存在
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 把用户代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * @param userCodeFile
     * @return com.chenxin.playcodesandbox.model.ExecuteMessage
     * @description 编译代码
     * @author fangchenxin
     * @date 2024/6/24 10:52
     */
    public ExecuteMessage compileFile(File userCodeFile) {
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.getProcessInfo(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException("编译错误");
            }
            return executeMessage;
        } catch (Exception e) {
            //return getErrorResponse(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * @param userCodeFile
     * @param inputArgs
     * @return java.util.List<com.chenxin.playcodesandbox.model.ExecuteMessage>
     * @description 执行代码
     * @author fangchenxin
     * @date 2024/6/24 16:52
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputArgs) {
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        File userCodeParentPath = userCodeFile.getParentFile().getAbsoluteFile();
        for (String inputArg : inputArgs) {
            String runCmd = String.format("java -Xmx256m -cp %s Main %s", userCodeParentPath, inputArg);
            log.info("执行命令: " + runCmd);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 新开一个线程,监听超时
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        if (runProcess.isAlive()) {
                            log.warn("程序执行超时，强制中断");
                            runProcess.destroy();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.getProcessInfo(runProcess, "运行");
                executeMessageList.add(executeMessage);
                log.info(executeMessage.toString());

            } catch (Exception e) {
                throw new RuntimeException("执行错误", e);
            }
        }
        return executeMessageList;
    }

    /**
     * @param executeMessageList
     * @return com.chenxin.playcodesandbox.model.ExecuteCodeResponse
     * @description 输出结果
     * @author fangchenxin
     * @date 2024/6/24 15:19
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 取用时最大值
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                // todo
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
        }
        // 正常运行完成
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        // todo
        judgeInfo.setMemory(1L);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * @param userCodeFile
     * @return boolean
     * @description 删除文件
     * @author fangchenxin
     * @date 2024/6/24 16:50
     */
    public boolean deleteFile(File userCodeFile) {
        File userCodeParentPath = userCodeFile.getParentFile().getAbsoluteFile();
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            log.info("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * @param e
     * @return com.chenxin.playcodesandbox.model.ExecuteCodeResponse
     * @description 获取错误响应
     * @author fangchenxin
     * @date 2024/6/18 23:44
     */
    public ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 代码沙箱执行错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputArgs = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        // 把用户代码保存为文件
        File userCodeFile = saveCodeToFile(code);

        // 编译代码
        ExecuteMessage comileFileExecuteMessage = compileFile(userCodeFile);
        log.info("编译结果：" + comileFileExecuteMessage);

        // 执行文件
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputArgs);

        // 输出结果
        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);

        // 删除文件
        boolean res = deleteFile(userCodeFile);
        if (!res) {
            log.error("deleteFile error, user");
        }

        return outputResponse;
    }

}
