package com.xiaobaicai.stress.testing.app.agent.core;

import java.util.HashMap;
import java.util.Map;

/**
 * @author xiaobaicai
 * @description
 * @date 2024/12/10 星期二 18:19
 */
public class AgentConfig {
    private static final Map<String, String> properties = new HashMap<>();
    private static final String SHADOW_MODE_KEY = "shadowMode";
    public static final String SHADOW_MODE_DB = "DB";
    public static final String SHADOW_MODE_TABLE = "TABLE";
    private static final String DEFAULT_SHADOW_MODE = SHADOW_MODE_TABLE;

    public static void readArgs(String agentArgs) {
        String[] args = agentArgs.split("&");
        for (String arg : args) {
            String[] param = arg.split("=");
            properties.put(param[0], param[1]);
        }
    }

    private static String getProperty(String key) {
        return properties.get(key);
    }

    public static String getShadowMode() {
        String shadowModel = properties.get(SHADOW_MODE_KEY);
        return shadowModel == null ? DEFAULT_SHADOW_MODE : shadowModel;
    }
}
