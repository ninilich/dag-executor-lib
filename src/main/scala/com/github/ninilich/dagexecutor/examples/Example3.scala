package com.github.ninilich.dagexecutor.examples

import com.github.ninilich.dagexecutor.{DAG, RunnableDAGTask}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.sum
import org.slf4j.{Logger, LoggerFactory}

/** Example with getTasks, which return Unit
  */
object Example3 extends App {

  implicit val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  implicit val spark: SparkSession = SparkSession.builder
    .appName(s"Dag-example-with-Spark-app")
    .master("local[*]") // Set the master URL as needed
    .config("spark.ui.enabled", "false") // Disable Spark UI, it's only example
    .getOrCreate()
  spark.sparkContext.setLogLevel("ERROR")

  /** Dummy Spark job
    */
  class SparkJob(rowNumber: Int)(implicit spark: SparkSession) extends RunnableDAGTask[Unit] {
    override def run(): Option[Unit] = {
      // Define the schema explicitly
      val df = spark.range(0, rowNumber)
      val sumValue = df.agg(sum("id")).first().getLong(0)
      logger.info(s"Sum of values = $sumValue")
    }
  }

  logger.info("Starting Example3.")

  val dag = new DAG[Unit]()

  val job1 = new SparkJob(10)
  val job2 = new SparkJob(20)
  val job3 = new SparkJob(30)
  val job4 = new SparkJob(40)
  val job5 = new SparkJob(90)
  val job6 = new SparkJob(30)

  dag.addTask("Job 1", job1) // Job 1 has no dependencies
  dag.addTask("Job 2", job2, List("Job 1")) // Job 2 depends on Job 1
  dag.addTask("Job 3", job3, List("Job 1")) // Job 3 depends on Job 1
  dag.addTask("Job 4", job4, List("Job 2", "Job 3")) // Job 4 depends on Job 2 and Job 3
  dag.addTask("Job 5", job5, List("Job 1")) // Job 5 depends on Job 1
  dag.addTask("Job 6", job6, List("Job 4", "Job 5")) // Job 6 depends on Job 4 and Job 5

  // Execute the DAG and get the results

  val executionResult = dag.execute()

  spark.stop()

}
