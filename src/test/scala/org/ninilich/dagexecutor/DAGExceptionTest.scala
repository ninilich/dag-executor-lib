package org.ninilich.dagexecutor

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.{Logger, LoggerFactory}

import java.io.{PrintWriter, StringWriter}

class DAGExceptionTest extends AnyFlatSpec with Matchers {

  implicit val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  /** Conversion from Throwable to String
    */
  private def throwableToString(t: Throwable): String = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    t.printStackTrace(pw)
    sw.toString
  }

  /** Mock implementation of RunnableDAGTask for testing
    */
  private class MockDAGTask(val duration: Long) extends RunnableDAGTask[Long] {
    override def run(): Option[Long] = Some(duration)
  }

  /** Mock implementation of RunnableDAGTask for testing exceptions
    */
  private class FailingDAGTask extends RunnableDAGTask[Unit] {
    override def run(): Option[Unit] = {
      throw new RuntimeException(s"This is a test exception") // Simulate a failure
    }
  }

  it should "throw an exception when adding a task with non-existent dependencies" in {
    val dag = new DAG[Long]()
    val task = new MockDAGTask(100)

    an[Exception] should be thrownBy {
      dag.addTask("Task", task, List("NonExistentTask"))
    }
  }

  it should "throw an exception if DAG execution fails, including the failed task and the original task exception in the message." in {
    val dag = new DAG[Unit]()
    val task = new FailingDAGTask
    dag.addTask("FailingDAGTask", task)

    an[Exception] should be thrownBy { dag.execute() }

    val exceptionResult =
      try {
        dag.execute()
        None
      } catch {
        case e: Exception => Some(e)
      }

    exceptionResult match {
      case Some(e) =>
        e.getMessage shouldEqual "DAG execution failed"

        val stackTrace = throwableToString(e)
        stackTrace should include("Task 'FailingDAGTask' failed")
        stackTrace should include("This is a test exception")

      case None =>
        fail("Expected an exception but got none.")
    }
  }
}
