package org.ninilich.dagexecutor

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.slf4j.{Logger, LoggerFactory}

class DAGOutputTest extends AnyFunSuite with Matchers {
  implicit val logger: Logger = LoggerFactory.getLogger(getClass.getName)
  type Result = Map[String, String]

  // Dummy class to represent a task
  private class MockSomeTask(text: String) extends RunnableDAGTask[Map[String, String]] {
    override def run(): Option[Map[String, String]] = Some(Map(text -> text))
  }

  test("DAG should return the results of task execution correctly") {
    val dag = new DAG[Result]()

    // Output of task will be the Map(taskName -> taskName)
    val job1 = new MockSomeTask("Job 1")
    val job2 = new MockSomeTask("Job 2")
    val job3 = new MockSomeTask("Job 3")
    val job4 = new MockSomeTask("Job 4")
    val job5 = new MockSomeTask("Job 5")
    val job6 = new MockSomeTask("Job 6")

    // Adding nodes to the DAG with dependencies
    dag.addTask("Job 1", job1)
    dag.addTask("Job 2", job2, List("Job 1"))
    dag.addTask("Job 3", job3, List("Job 1"))
    dag.addTask("Job 4", job4, List("Job 2", "Job 3"))
    dag.addTask("Job 5", job5, List("Job 1"))
    dag.addTask("Job 6", job6, List("Job 4", "Job 5"))

    // Execute the DAG and check the results
    val executionResult = dag.execute()

    executionResult.foreach { res =>
      val output = res.taskOutput.getOrElse(res.taskName, "-")
      assert(output == Map(res.taskName -> res.taskName))
    }
  }
}
