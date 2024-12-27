package com.xiaobaicai.stress.testing.app.agent.core;

import cn.hutool.core.collection.CollectionUtil;
import com.xiaobaicai.stress.testing.app.agent.core.trace.LocalSpan;

import java.util.*;

/**
 * @author xiaobaicai
 * @description 关注微信公众号【程序员小白菜】领取源码
 * @date 2024/12/9 星期一 14:05
 */
public class ContextManager {

    public static final ThreadLocal<Map<String, Object>> LOCAL_PROPERTY = new ThreadLocal<>();

    public static final ThreadLocal<String> LOCAL_TRACE_ID = new ThreadLocal<>();

    public static final ThreadLocal<LinkedList<String>> LOCAL_SPAN_ID_STACK = new ThreadLocal<>();

    public static final ThreadLocal<Map<String, LocalSpan>> LOCAL_SPAN_CACHE = new ThreadLocal<>();

    public static final ThreadLocal<LocalSpan> LOCAL_ROOT_SPAN = new ThreadLocal<>();


    public static void setProperty(String key, Object value) {
        Map<String, Object> properties = LOCAL_PROPERTY.get();
        if (properties == null) {
            properties = new HashMap<>();
            LOCAL_PROPERTY.set(properties);
        }
        properties.put(key, value);
    }

    public static Object getProperty(String key) {
        Map<String, Object> properties = LOCAL_PROPERTY.get();
        return properties == null ? null : properties.get(key);
    }

    public static void createSpan(String operateName) {
        boolean root = isRoot();
        if (root) {
            init();
        }
        String spanId = UUID.randomUUID().toString();
        LocalSpan localSpan = LocalSpan.builder()
                .isRoot(root)
                .traceId(getGlobalTraceId())
                .spanId(spanId)
                .parentSpanId(getParentSpanId())
                .operateName(operateName)
                .build();
        register(localSpan);
        localSpan.start();
    }

    public static void register(LocalSpan localSpan) {
        String spanId = localSpan.getSpanId();
        LOCAL_SPAN_ID_STACK.get().addLast(spanId);
        LOCAL_SPAN_CACHE.get().put(spanId, localSpan);
        if (localSpan.isRoot()) {
            LOCAL_ROOT_SPAN.set(localSpan);
        }
    }

    public static void remove() {
        LOCAL_TRACE_ID.remove();
        LOCAL_SPAN_ID_STACK.remove();
        LOCAL_SPAN_CACHE.remove();
        LOCAL_ROOT_SPAN.remove();
        LOCAL_PROPERTY.remove();
    }

    public static void init() {
        LOCAL_TRACE_ID.set(UUID.randomUUID().toString());
        LOCAL_SPAN_ID_STACK.set(new LinkedList<>());
        LOCAL_SPAN_CACHE.set(new LinkedHashMap<>());
    }

    public static String getGlobalTraceId() {
        return LOCAL_TRACE_ID.get();
    }

    public static String getParentSpanId() {
        LinkedList<String> methodInvokeList = LOCAL_SPAN_ID_STACK.get();
        if (methodInvokeList.isEmpty()) {
            return "0";
        }
        return methodInvokeList.getLast();
    }

    public static boolean isRoot() {
        return LOCAL_TRACE_ID.get() == null;
    }

    public static void finishSpan() {
        // 获取将要出栈的Span
        LinkedList<String> methodStack = LOCAL_SPAN_ID_STACK.get();
        String spanId = methodStack.peekLast();
        LocalSpan localSpan = LOCAL_SPAN_CACHE.get().get(spanId);
        // 耗时记录
        localSpan.finish();
        // 出栈
        methodStack.removeLast();

        if (methodStack.isEmpty()) {
            // 打印trace链路
            printTraceInformation();
            // 释放资源
            remove();
        }
    }

    public static void printTraceInformation() {
        Map<String, LocalSpan> spanMap = LOCAL_SPAN_CACHE.get();
        LocalSpan rootSpan = LOCAL_ROOT_SPAN.get();
        int depth = 0;
        String spanId = rootSpan.getSpanId();
        StringBuilder sb = new StringBuilder();
        sb.append(rootSpan.getOperateName()).append("  ").append(rootSpan.getCostTime()).append("ms").append("\n");
        appendChildren(spanMap, spanId, depth, sb);
        System.out.println(sb);
    }

    private static void appendChildren(Map<String, LocalSpan> spanMap, String spanId, int depth, StringBuilder sb) {
        List<Map.Entry<String, LocalSpan>> childSpanList = spanMap.entrySet().stream().filter(span -> spanId.equals(span.getValue().getParentSpanId())).toList();
        if (CollectionUtil.isEmpty(childSpanList)) {
            return;
        }
        depth++;
        for (Map.Entry<String, LocalSpan> childSpanEntry : childSpanList) {
            LocalSpan childSpan = childSpanEntry.getValue();
            sb.append("     ".repeat(depth));
            sb.append(childSpan.getOperateName()).append("  ").append(childSpan.getCostTime()).append("ms").append("\n");
            appendChildren(spanMap, childSpanEntry.getKey(), depth, sb);
        }
    }
}
