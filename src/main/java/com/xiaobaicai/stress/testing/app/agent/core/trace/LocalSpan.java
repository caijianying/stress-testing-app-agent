package com.xiaobaicai.stress.testing.app.agent.core.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author xiaobaicai
 * @description Span
 * @date 2024/12/16 星期一 15:42
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LocalSpan implements Serializable {
    @Serial
    private static final long serialVersionUID = -7335003348952891472L;

    private boolean isRoot;
    private String traceId;
    private String spanId;
    private String parentSpanId;

    /**
     * 链路名称
     **/
    private String operateName;

    private Long startTime;

    private Long costTime;

    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    public void finish() {
        this.costTime = System.currentTimeMillis() - startTime;
    }
}
