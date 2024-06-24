package com.chenxin.playcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.chenxin.playcodesandbox.model.ExecuteCodeRequest;
import com.chenxin.playcodesandbox.model.ExecuteCodeResponse;
import com.chenxin.playcodesandbox.model.ExecuteMessage;
import com.chenxin.playcodesandbox.model.JudgeInfo;
import com.chenxin.playcodesandbox.security.DefaultSecurityManager;
import com.chenxin.playcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @author fangchenxin
 * @description
 * @date 2024/6/18 15:42
 * @modify
 */
@Slf4j
public class JavaNativeCodeSandbox implements CodeSandbox {

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    public static final List<String> blackList = Arrays.asList("Files", "exec");

    private static final WordTree wordTree = new WordTree();

    public static final String SECURITY_MANAGER_PATH = "/Users/fangchenxin/Desktop/yupi/code/OJ/play-code-sandbox/src/main/resources/security/";

    public static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

    static {
        // 初始化字典树
        wordTree.addWords(blackList);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        //System.setSecurityManager(new DefaultSecurityManager());
        List<String> inputArgs = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        // 校验代码是否包含黑名单中命令
//        FoundWord foundWord = wordTree.matchWord(code);
//        if (foundWord != null) {
//            log.warn("发现敏感词：" + foundWord.getFoundWord());
//            return null;
//        }

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

        // 编译代码
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.getProcessInfo(compileProcess, "编译");
            log.info(executeMessage.toString());
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        // 执行代码
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArg : inputArgs) {
            //java -Xmx256m
            //String runCmd = String.format("java -Xmx256m -cp %s Main %s", userCodeParentPath, inputArg);
            String runCmd = String.format("java -cp %s:%s -Djava.security.manager=%s Main %s", userCodeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, inputArg);
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
                return getErrorResponse(e);
            }
        }

        // 整理输出
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

        // 文件处理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            log.info("删除" + (del ? "成功" : "失败"));
        }

        return executeCodeResponse;
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

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        String code = ResourceUtil.readStr("testcode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
        //String code = ResourceUtil.readStr("testcode/simplecomputeargs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }
}
