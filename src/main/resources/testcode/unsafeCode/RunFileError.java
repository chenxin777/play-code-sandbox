import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author fangchenxin
 * @description
 * @date 2024/6/19 12:59
 * @modify
 */
public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        String userDir = System.getProperty("user.dir");
        String filePath = userDir + File.separator + "src/main/resources/木马.sh";
        Process process = Runtime.getRuntime().exec(filePath);
        process.waitFor();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder compileOutputStringBuilder = new StringBuilder();
        String compileOutputLine;
        // 逐行读取
        while ((compileOutputLine = bufferedReader.readLine()) != null) {
            compileOutputStringBuilder.append(compileOutputLine).append("\n");
        }
        System.out.println(compileOutputStringBuilder);
    }
}
