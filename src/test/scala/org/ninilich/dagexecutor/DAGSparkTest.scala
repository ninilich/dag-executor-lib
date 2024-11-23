package org.ninilich.dagexecutor

import org.apache.spark.sql.functions.{col, lit, sum}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.slf4j.{Logger, LoggerFactory}

class DAGSparkTest extends AnyFunSuite with Matchers with SparkSessionForTestsWrapper {

  implicit val logger: Logger = LoggerFactory.getLogger(getClass.getName)
  private val csvFilePath = getClass.getResource("/testData.csv").getPath

  /** Mock implementation of RunnableDAGTask for testing
    */
  private class MockSparkTask(value: Long) extends RunnableDAGTask[Long] {
    override def run(): Option[Long] = {
      val df = spark.read
        .option("header", "true")
        .option("inferSchema", "true")
        .csv(csvFilePath)

      Some(
        df
          .filter(col("Col1") === lit(value))
          .groupBy(col("Col1"))
          .agg(sum(col("Col2")).as("Sum"))
          .collect()(0)
          .getLong(1)
      )
    }
  }

  test("DAG should execute Spark-jobs correctly and return correct results") {
    val dag = new DAG[Long]()
    val taskA1 = new MockSparkTask(1)
    val taskA2 = new MockSparkTask(2)
    val taskB1 = new MockSparkTask(3)
    val taskB2 = new MockSparkTask(4)
    val taskC = new MockSparkTask(5)
    val taskD = new MockSparkTask(6)

    /* Output DAG:
            -- A1 --------
          /               \
    Start --  A2 - B1 - C - D -- End
               \      /
                - B2 -
     */
    dag.addTask("TaskA1", taskA1)
    dag.addTask("TaskA2", taskA2)
    dag.addTask("TaskB1", taskB1, List("TaskA2")) // Depends on TaskA
    dag.addTask("TaskB2", taskB2, List("TaskA2")) // Depends on TaskA
    dag.addTask("TaskD", taskC, List("TaskB1", "TaskB2")) // Depends on TaskB and TaskC
    dag.addTask("TaskE", taskD, List("TaskD", "TaskA1")) // Depends on TaskD and TaskA1

    val executionResults = dag.execute()
    val resultsToCompare = executionResults.sortBy(_.taskName).map(_.taskOutput.getOrElse(-100))
    resultsToCompare shouldEqual (1 to 6).map(_ * 5)
  }
}
