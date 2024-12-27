package com.xiaobaicai.stress.testing.app.agent.core;

import java.util.Timer;
import java.util.TimerTask;

/**
 * @author xiaobaicai
 * @description 关注微信公众号【程序员小白菜】领取源码
 * @date 2024/12/20 星期五 16:26
 */
public class MeltDownManager {

    private static final int SECONDS_TO_MELTDOWN = 10 * 1000;

    private static volatile boolean NEED_MELTDOWN = false;

    /**
     * 开启定时任务，检测方法是否已执行
     **/
    public static long meltDownIfNecessary() {
        long start = System.currentTimeMillis();
        Timer timer = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                System.out.println("校验熔断条件...");
                if (NEED_MELTDOWN) {
                    NEED_MELTDOWN = false;
                    System.out.println("发起压测熔断，通知压测引擎中止压测任务.");
                }
                if (System.currentTimeMillis() - start >= SECONDS_TO_MELTDOWN) {
                    timer.cancel();
                }
            }
        };
        timer.schedule(timerTask, 0, 1000);
        return start;
    }

    /**
     * 如果超过了10s，则标记熔断
     **/
    public static void markMeltDownFlag(long start) {
        if (System.currentTimeMillis() - start >= SECONDS_TO_MELTDOWN) {
            // 需要做熔断通知的标识
            NEED_MELTDOWN = true;
        }
    }

}
