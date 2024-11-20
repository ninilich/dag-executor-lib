package org.ninilich.dagexecutor.examples

import org.ninilich.dagexecutor.{DAG, RunnableDAGTask}
import org.slf4j.{Logger, LoggerFactory}

/** Example with getTasks, which return Unit
  */
object Example1 extends App {

  implicit val logger: Logger = LoggerFactory.getLogger(getClass)

  /** Dummy class to represent a task
    */
  class SomeTask(timeout: Int) extends RunnableDAGTask[Unit] {

    override def run(): Option[Unit] = {
      logger.info(s"Working...") // logging
      Thread.sleep(timeout) // simulating some work
    }
  }

  logger.info("Starting Example1.")

  val dag = new DAG[Unit]()

  val job1 = new SomeTask(100)
  val job2 = new SomeTask(200)
  val job3 = new SomeTask(300)
  val job4 = new SomeTask(200)
  val job5 = new SomeTask(900)
  val job6 = new SomeTask(300)

  // Adding nodes to the DAG with dependencies, using task names
  dag.addTask("Job 1", job1) // Job 1 has no dependencies
  dag.addTask("Job 2", job2, List("Job 1")) // Job 2 depends on Job 1
  dag.addTask("Job 3", job3, List("Job 1")) // Job 3 depends on Job 1
  dag.addTask("Job 4", job4, List("Job 2", "Job 3")) // Job 4 depends on Job 2 and Job 3
  dag.addTask("Job 5", job5, List("Job 1")) // Job 5 depends on Job 1
  dag.addTask("Job 6", job6, List("Job 4", "Job 5")) // Job 6 depends on Job 4 and Job 5

  logger.info(dag.getTasks.toString()) // Print the getTasks of the DAG

  val executionResult = dag.execute()
  executionResult.foreach(res => logger.info(s"Execution if task '${res.taskName}' took ${res.taskDurationSec} sec."))

}
