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
    public List<Extension> getAdditionalExtensions() {
        return Collections.singletonList(
                /**
                 * 这是lambda表达式的开始，它接受两个参数：testInstance和context。
                 * 这个lambda表达式实际上是一个TestInstancePostProcessor，用于处理测试实例。
                 * TestInstancePostProcessor：测试实例后置处理器
                 **/
                (TestInstancePostProcessor) (testInstance, context) -> {
                    final Class clazz = testInstance.getClass();
                    // Group test contexts by test class
                    /**
                     *ConcurrentHashMap<Class, List<EvaluationContext>> ACTIVE_CONTEXTS
                     * EvaluationContext：测试实例，测试方法，测试方法名称，开始时间，统计者，加载配置，evaluationRequire：限定，结果
                     * 加载配置，加载评判标准，运行校验
                     **/
                    ACTIVE_CONTEXTS.putIfAbsent(clazz, new ArrayList<>());
                    /**
                     *构造一个EvaluationContext：评估环境
                     **/
                    EvaluationContext evaluationContext = new EvaluationContext(testInstance, method, DateUtil.getCurrentDateTimeStr());
                   /**
                    *评估相关信息内容中加载配置
                    * JunitPerfConfig：执行时使用多少线程执行/准备时间（单位：毫秒）/执行时间。（单位：毫秒）/
                    **/
                    evaluationContext.loadConfig(perfConfig);
                   /**
                    *评估相关信息内容中加载限定
                    * JunitPerfRequire 注解：
                    *min:最快的运行耗时如果高于这个值，则视为失败
                    * max:最坏的运行耗时如果高于这个值，则视为失败
                    * average:平均的运行耗时如果高于这个值，则视为失败
                    * percentiles：对于执行耗时的限定，percentiles={"20:220", "30:250"}
                    * 20% 的数据执行耗时不得超过 220ms;
                    * 30% 的数据执行耗时不得超过 250ms;
                    * timesPerSecond：如果低于这个最小执行次数，则视为失败
                    **/
                    evaluationContext.loadRequire(perfRequire);
                    /**
                     *statistics() default DefaultStatisticsCalculator.class;
                     *实例化统计计算器对象
                     **/
                    StatisticsCalculator statisticsCalculator = perfConfig.statistics().newInstance();
                    /**
                     *获取报告器集合
                     **/
                    Set<Reporter> reporterSet = getReporterSet();
                    /**
                     *ConcurrentHashMap<Class, List<EvaluationContext>> ACTIVE_CONTEXTS
                     *  EvaluationContext：测试实例，测试方法，测试方法名称，开始时间，统计者，加载配置，evaluationRequire：限定，结果
                     *                     加载配置，加载评判标准，运行校验
                     *  Class clazz = testInstance.getClass()；当前测试 class 信息
                     *   EvaluationContext evaluationContext = new EvaluationContext(testInstance, method, DateUtil.getCurrentDateTimeStr());
                     *  将同一个测试实例的评估相关信息内容放入同一集合
                     **/
                    ACTIVE_CONTEXTS.get(clazz).add(evaluationContext);
                    try {
                        /**
                         *性能测试 statement
                         *      * 性能测试接口定义
                         *      *
                         *      * @param evaluationContext    上下文
                         *      * @param statisticsCalculator 统计
                         *      * @param reporterSet          报告方式
                         *      * @param evaluationContextList 上下文
                         *      * @param testClass            当前测试 class 信息
                         *TODO:
                         **/

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
        /**
         *Reporter->void report(Class testClass, Collection<EvaluationContext> evaluationContextSet);
         *  public void report(Class testClass, Collection<EvaluationContext> evaluationContextSet) {
         *         for (EvaluationContext context : evaluationContextSet) {...}
         *
         **/
        Set<Reporter> reporterSet = new HashSet<>();
        /**
         * perfConfig.reporter() default {ConsoleReporter.class};
         **/
        Class<? extends Reporter>[] reporters = perfConfig.reporter();
        /**
         *可能存在多个reporter
         **/
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
