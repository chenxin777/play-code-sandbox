/**
 * @author fangchenxin
 * @description 无限睡眠
 * @date 2024/6/19 11:17
 * @modify
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        long ONE_HOUR = 60 * 60 * 1000L;
        Thread.sleep(ONE_HOUR);
        System.out.println("睡完了");
    }
}
