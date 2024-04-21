package com.github.houbb.junitperf.core.jupiter.context;

import com.github.houbb.heaven.util.util.DateUtil;
import com.github.houbb.junitperf.constant.VersionConstant;
import com.github.houbb.junitperf.core.annotation.JunitPerfConfig;
import com.github.houbb.junitperf.core.annotation.JunitPerfRequire;
import com.github.houbb.junitperf.core.report.Reporter;
import com.github.houbb.junitperf.core.statistics.StatisticsCalculator;
import com.github.houbb.junitperf.model.evaluation.EvaluationContext;
import com.github.houbb.junitperf.support.exception.JunitPerfRuntimeException;
import com.github.houbb.junitperf.support.statements.PerformanceEvaluationStatement;

import org.apiguardian.api.API;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p> 配置上下文 </p>
 *
 * <pre> Created: 2018/6/28 下午5:01  </pre>
 * <pre> Project: junitperf  </pre>
 *
 * @author houbinbin
 * @version 2.0.0
 * @since 2.0.0
 */
@API(status = API.Status.INTERNAL, since = VersionConstant.V2_0_0)
public class PerfConfigContext implements TestTemplateInvocationContext {

    /**
     * 用于存储上下文
     */
    private static final ConcurrentHashMap<Class, List<EvaluationContext>> ACTIVE_CONTEXTS = new ConcurrentHashMap<>();
    private final        Method                                           method;
    private              JunitPerfConfig                                  perfConfig;
    private              JunitPerfRequire                                 perfRequire;


    /**
     * @Author sinian
     * @Description :拿到需要测试的方法，及其方法上的JunitPerfConfig和JunitPerfRequire注解
     * @Date 2024/4/21 18:10
     * @Param [context]
     * @return
     **/


    public PerfConfigContext(ExtensionContext context) {
        this.method = context.getRequiredTestMethod();//获取需要测试的方法
        this.perfConfig = method.getAnnotation(JunitPerfConfig.class);//获取方法上的JunitPerfConfig注解
        this.perfRequire = method.getAnnotation(JunitPerfRequire.class);//获取方法上的JunitPerfRequire注解
    }


    /**
     * @Author sinian
     * @Description :对测试实例进行性能评估，并生成相应的报告。
     * 在评估过程中，会创建一个EvaluationContext对象，加载配置信息和要求信息，实例化统计计算器对象，获取报告器集合等操作。
     * 最后，通过PerformanceEvaluationStatement类对评估过程进行处理，并捕获可能的异常
     * @Date 2024/4/21 18:12
     * @Param []
     * @return java.util.List<org.junit.jupiter.api.extension.Extension>
     **/

    @Override
    public List<Extension> getAdditionalExtensions() {//框架触发扩展方法是启动项
        return Collections.singletonList(
                (TestInstancePostProcessor) (testInstance, context) -> {
                    final Class clazz = testInstance.getClass();
                    // Group test contexts by test class
                    ACTIVE_CONTEXTS.putIfAbsent(clazz, new ArrayList<>());

                    EvaluationContext evaluationContext = new EvaluationContext(testInstance,
                            method,
                            DateUtil.getCurrentDateTimeStr());
                    evaluationContext.loadConfig(perfConfig);
                    evaluationContext.loadRequire(perfRequire);
                    StatisticsCalculator statisticsCalculator = perfConfig.statistics().newInstance();
                    Set<Reporter> reporterSet = getReporterSet();
                    ACTIVE_CONTEXTS.get(clazz).add(evaluationContext);
                    try {
                        new PerformanceEvaluationStatement(evaluationContext,
                                statisticsCalculator,
                                reporterSet,
                                ACTIVE_CONTEXTS.get(clazz),
                                clazz).evaluate();
                    } catch (Throwable throwable) {
                        throw new JunitPerfRuntimeException(throwable);
                    }
                }
        );
    }

    /**
     * 获取报告集合
     *
     * @return 报告集合
     */
    private Set<Reporter> getReporterSet() {
        Set<Reporter> reporterSet = new HashSet<>();
        Class<? extends Reporter>[] reporters = perfConfig.reporter();
        for (Class clazz : reporters) {
            try {
                Reporter reporter = (Reporter) clazz.newInstance();
                reporterSet.add(reporter);
            } catch (InstantiationException | IllegalAccessException e) {
                throw new JunitPerfRuntimeException(e);
            }
        }
        return reporterSet;
    }

}
