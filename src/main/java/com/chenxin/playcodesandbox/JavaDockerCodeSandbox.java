package com.chenxin.playcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.chenxin.playcodesandbox.model.ExecuteCodeRequest;
import com.chenxin.playcodesandbox.model.ExecuteCodeResponse;
import com.chenxin.playcodesandbox.model.ExecuteMessage;
import com.chenxin.playcodesandbox.model.JudgeInfo;
import com.chenxin.playcodesandbox.utils.ProcessUtils;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author fangchenxin
 * @description
 * @date 2024/6/18 15:42
 * @modify
 */
@Slf4j
public class JavaDockerCodeSandbox implements CodeSandbox {

    public static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    public static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    public static final List<String> blackList = Arrays.asList("Files", "exec");

    private static final WordTree wordTree = new WordTree();

    public static final String SECURITY_MANAGER_PATH = "/Users/fangchenxin/Desktop/yupi/code/OJ/play-code-sandbox/src/main/resources/security/";

    public static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

    /**
     * docker挂载宿主机路径
     */
    public static final String VOLUME_PATH = "/Users/fangchenxin/Desktop/yupi/code/OJ/userCode";

    public static final Boolean FIRST_INIT = true;

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

        // 创建容器，把文件复制到容器内
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 拉取镜像java
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    log.info("下载镜像: " + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
                log.info("首次拉取Java镜像成功");
            } catch (InterruptedException e) {
                log.info("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }

        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        //hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume(VOLUME_PATH)));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        String containerId = createContainerResponse.getId();
        log.info("创建容器id：" + containerId);

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        // 执行命令获取结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArg : inputArgs) {
            StopWatch stopWatch = new StopWatch();

            String[] strings = inputArg.split(" ");
            String[] cmdArr = new String[]{"java", "-cp", VOLUME_PATH, "Main"};
            cmdArr = ArrayUtil.append(cmdArr, strings);
            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArr)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .exec();
            log.info("创建执行命令：" + exec);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0L;
            // 判断是否超时
            final boolean[] timeout = {true};
            // 定义执行回调
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        log.info("输出错误结果：" + new String(frame.getPayload()));

                    } else {
                        message[0] = new String(frame.getPayload());
                        log.info("输出结果：" + new String(frame.getPayload()));
                    }
                    super.onNext(frame);
                }

                @Override
                public void onComplete() {
                    // 如果执行完成
                    timeout[0] = false;
                    super.onComplete();
                }
            };
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    log.info("内存占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void onStart(Closeable closeable) {

                }


                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });

            try {
                stopWatch.start();
                dockerClient.execStartCmd(exec.getId()).exec(execStartResultCallback).awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                statsCmd.close();
                // 获取执行时间
                time = stopWatch.getLastTaskTimeMillis();
                executeMessage.setMessage(message[0]);
                executeMessage.setErrorMessage(errorMessage[0]);
                executeMessage.setTime(time);
                executeMessage.setMemory(maxMemory[0]);
                executeMessageList.add(executeMessage);
            } catch (InterruptedException e) {
                log.error("程序执行异常");
                throw new RuntimeException(e);
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

    public static void main(String[] args) throws InterruptedException {
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        //String code = ResourceUtil.readStr("testcode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
        String code = ResourceUtil.readStr("testcode/simplecomputeargs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        log.info(executeCodeResponse.toString());
    }
}
