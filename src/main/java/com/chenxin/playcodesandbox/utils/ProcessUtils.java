package com.chenxin.playcodesandbox.utils;

import cn.hutool.core.util.StrUtil;
import com.chenxin.playcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author fangchenxin
 * @description
 * @date 2024/6/18 18:15
 * @modify
 */
@Slf4j
public class ProcessUtils {

    /**
     * @param process
     * @param optName
     * @return com.chenxin.playcodesandbox.model.ExecuteMessage
     * @description 执行进程并获取信息
     * @author fangchenxin
     * @date 2024/6/18 18:31
     */
    public static ExecuteMessage getProcessInfo(Process process, String optName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            int exitValue = process.waitFor();
            executeMessage.setExitValue(exitValue);
            if (exitValue == 0) {
                log.info(optName + "成功");
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                List<String> outputList = new ArrayList<>();
                String compileOutputLine;
                // 逐行读取
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    outputList.add(compileOutputLine);
                }
                executeMessage.setMessage(StrUtil.join( "\n", outputList));
            } else {
                log.info(optName + "失败，错误码：" + exitValue);
                // 分批获取进程的正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                List<String> outputList = new ArrayList<>();
                String compileOutputLine;
                // 逐行读取
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    outputList.add(compileOutputLine);
                }
                executeMessage.setMessage(StrUtil.join( "\n", outputList));

                // 分批获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                List<String> errorOutputList = new ArrayList<>();
                String errorCompileOutputLine;
                // 逐行读取
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorOutputList.add(errorCompileOutputLine);
                }
                executeMessage.setErrorMessage(StrUtil.join( "\n", errorOutputList));
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return executeMessage;

    }

    /**
     * @param process
     * @param optName
     * @param args
     * @return com.chenxin.playcodesandbox.model.ExecuteMessage
     * @description 交互式程序
     * @author fangchenxin
     * @date 2024/6/18 20:12
     */
    public static ExecuteMessage getInteractiveProcessInfo(Process process, String optName, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            InputStream inputStream = process.getInputStream();
            OutputStream outputStream = process.getOutputStream();
            // 获取控制台输入
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] argArr = args.split(" ");
            outputStreamWriter.write(StrUtil.join("\n", argArr) + "\n");
            // 写入
            outputStreamWriter.flush();

            // 分批获取进程的正常输出
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder compileOutputStringBuilder = new StringBuilder();
            String compileOutputLine;
            // 逐行读取
            while ((compileOutputLine = bufferedReader.readLine()) != null) {
                compileOutputStringBuilder.append(compileOutputLine);
            }
            executeMessage.setMessage(optName + "成功信息：" + compileOutputStringBuilder);
            // 资源回收
            outputStreamWriter.close();
            outputStream.close();
            inputStream.close();
            process.destroy();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return executeMessage;

    }
}
