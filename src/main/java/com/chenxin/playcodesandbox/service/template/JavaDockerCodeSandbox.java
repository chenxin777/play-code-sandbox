package com.chenxin.playcodesandbox.service.template;

import cn.hutool.core.util.ArrayUtil;
import com.chenxin.playcodesandbox.model.ExecuteMessage;
import com.chenxin.playcodesandbox.service.template.JavaCodeSandboxTemplate;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author fangchenxin
 * @description
 * @date 2024/6/18 15:42
 * @modify
 */
@Component
@Slf4j
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final long TIME_OUT = 5000L;

    /**
     * docker挂载宿主机路径
     */
    public static final String VOLUME_PATH = "/Users/fangchenxin/Desktop/yupi/code/OJ/userCode";

    public static final Boolean FIRST_INIT = true;

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputArgs) {
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
        hostConfig.setBinds(new Bind(userCodeFile.getParentFile().getAbsolutePath(), new Volume(VOLUME_PATH)));
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
        return executeMessageList;
    }

}
