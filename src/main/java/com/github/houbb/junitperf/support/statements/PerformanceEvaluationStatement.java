package com.github.houbb.junitperf.support.statements;

import com.github.houbb.heaven.util.lang.ThreadUtil;
import com.github.houbb.junitperf.constant.VersionConstant;
import com.github.houbb.junitperf.core.report.Reporter;
import com.github.houbb.junitperf.core.statistics.StatisticsCalculator;
import com.github.houbb.junitperf.model.evaluation.EvaluationContext;
import com.github.houbb.junitperf.model.evaluation.component.EvaluationConfig;
import com.github.houbb.junitperf.support.exception.JunitPerfRuntimeException;
import com.github.houbb.junitperf.support.i18n.I18N;
import com.github.houbb.junitperf.support.task.PerformanceEvaluationTask;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apiguardian.api.API;

import java.util.*;
import java.util.concurrent.*;

/**
 * 性能测试 statement
 *
 * @author bbhou
 * @version 2.0.0
 * @since 1.0.0, 2018/01/11
 */
@API(status = API.Status.INTERNAL, since = VersionConstant.V2_0_0)
public class PerformanceEvaluationStatement {

    private static final String        THREAD_NAME_PATTERN = "performance-evaluation-thread-%d";
    private static final ThreadFactory FACTORY             = new ThreadFactoryBuilder().setNameFormat(THREAD_NAME_PATTERN).build();

    private final EvaluationContext      evaluationContext;
    private final StatisticsCalculator   statisticsCalculator;
    private final Set<Reporter>          reporterSet;
    private final Collection<EvaluationContext> evaluationContextList;
    private final Class                  testClass;

    /**
     * 性能测试接口定义
     *
     * @param evaluationContext    上下文
     * @param statisticsCalculator 统计
     * @param reporterSet          报告方式
     * @param evaluationContextList 上下文
     * @param testClass            当前测试 class 信息
     */
    public PerformanceEvaluationStatement(EvaluationContext evaluationContext,
                                          StatisticsCalculator statisticsCalculator,
                                          Set<Reporter> reporterSet,
                                          Collection<EvaluationContext> evaluationContextList,
                                          final Class testClass) {
        this.evaluationContext = evaluationContext;
        this.statisticsCalculator = statisticsCalculator;
        this.reporterSet = reporterSet;
        this.evaluationContextList = evaluationContextList;
        this.testClass = testClass;
    }

    /**
     * 校验信息
     *
     * @throws Throwable 异常
     */
    public void evaluate() throws Throwable {
        /**
         *任务列表
         *      PerformanceEvaluationTask：
         *      * @param warmUpNs 准备时间
         *      * @param statisticsCalculator 统计
         *      * @param testInstance 测试实例
         *      * @param testMethod 测试方法
         *      * @since 1.0.0
         *      执行校验/评估/方法测试，通用的统计更新，获取消耗的时间(单位：毫秒)
         *
         **/
        List<PerformanceEvaluationTask> taskList = new LinkedList<>();

        try {
            EvaluationConfig evaluationConfig = evaluationContext.getEvaluationConfig();
            for (int i = 0; i < evaluationConfig.getConfigThreads(); i++) {
                PerformanceEvaluationTask task = new PerformanceEvaluationTask(evaluationConfig.getConfigWarmUp(),
                        statisticsCalculator,
                        evaluationContext.getTestInstance(),
                        evaluationContext.getTestMethod());
           /**
            *FACTORY -> Thread newThread(Runnable r);
            * task->PerformanceEvaluationTask implements Runnable
            **/
                Thread t = FACTORY.newThread(task);
                taskList.add(task);
                t.start();
            }
            /**
             *  Duration:运行时间
             * 主线程持续Duration一段时间
             **/
            Thread.sleep(evaluationConfig.getConfigDuration());
        } finally {
            /**
             *具体详情，当执行打断时，被打断的任务可能已经开始执行(尚未执行完)，会出现主线程往下走，被打断的线程也在继续走的情况
             **/
            for (PerformanceEvaluationTask task : taskList) {
                /**
                 *终止执行的任务
                 **/
                task.setContinue(false);
            }
        }

        evaluationContext.setStatisticsCalculator(statisticsCalculator);
        evaluationContext.runValidation();
        generateReporter();
    }

    /**
     * 报告生成
     */
    private synchronized void generateReporter() {
        //1. 列表为空
        if(reporterSet.isEmpty()) {
            final String info = I18N.get(I18N.Key.reportIsEmpty);
        }
        /**
         *返回可用最优线程
         **/
        int bestThreadNum = ThreadUtil.bestThreadNum(reporterSet.size());
        if(bestThreadNum <= 1) {
            //2. 是否为只有单个文件
            final Reporter reporter = reporterSet.iterator().next();
            reporter.report(testClass, evaluationContextList);
        } else {
            //3. 线程池
            ExecutorService executorService = Executors.newFixedThreadPool(bestThreadNum);

            List<Future<Void>> futureTasks = new ArrayList<>();
            for(final Reporter reporter : reporterSet) {
                Callable<Void> tocGenCallable = () -> {
                    reporter.report(testClass, evaluationContextList);
                    return null;
                };

                Future<Void> reporterFuture = executorService.submit(tocGenCallable);
                futureTasks.add(reporterFuture);
            }
            executorService.shutdown();

            try {
                for(Future<Void> reporterFuture : futureTasks) {
                    Void aVoid = reporterFuture.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                throw new JunitPerfRuntimeException(e);
            }
        }
    }

}
