package org.ninilich.dagexecutor

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.{Logger, LoggerFactory}

class DAGGeneralTest extends AnyFlatSpec with Matchers {

  private var executionOrder = List.empty[Int]
  implicit val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  /** Mock implementation of RunnableDAGTask for testing
    */
  private class MockDAGTask(val duration: Long, val flag: Int = 0) extends RunnableDAGTask[Long] {
    override def run(): Option[Long] = {
      Thread.sleep(duration) // Simulate task execution time
      executionOrder = flag :: executionOrder
      Some(duration)
    }
  }

  it should "execute getTasks in the correct order, allow parallel execution and return results" in {
    val dag = new DAG[Long]()
    executionOrder = List.empty[Int]

    val taskA = new MockDAGTask(100, 1)
    val taskA1 = new MockDAGTask(50, 1)
    val taskB1 = new MockDAGTask(200, 2)
    val taskB2 = new MockDAGTask(150, 2)
    val taskC = new MockDAGTask(100, 3)
    val taskD = new MockDAGTask(150, 4)

    /* Output DAG:
            -- A1 --------
          /               \
    Start --  A - B1 - C - D -- End
               \      /
                - B2 -
     */
    dag.addTask("TaskA", taskA)
    dag.addTask("TaskA1", taskA1)
    dag.addTask("TaskB1", taskB1, List("TaskA")) // Depends on TaskA
    dag.addTask("TaskB2", taskB2, List("TaskA")) // Depends on TaskA
    dag.addTask("TaskC", taskC, List("TaskB1", "TaskB2")) // Depends on TaskB and TaskC
    dag.addTask("TaskD", taskD, List("TaskC", "TaskA1")) // Depends on TaskD and TaskA1

    val executionResults = dag.execute()

    executionOrder shouldEqual List(4, 3, 2, 2, 1, 1)
    executionResults.sortBy(_.taskName).map(_.taskOutput.getOrElse(-1)) shouldEqual List(100, 50, 200, 150, 100, 150)
  }

  it should "execute only some of getTasks in the correct order" in {
    val dag = new DAG[Long]()
    executionOrder = List.empty[Int]

    val taskA = new MockDAGTask(100, 0)
    val taskA1 = new MockDAGTask(50, 1)
    val taskB1 = new MockDAGTask(200, 1)
    val taskB2 = new MockDAGTask(150, 0)
    val taskC = new MockDAGTask(100, 2)
    val taskD = new MockDAGTask(150, 3)

    /* DAG:
            -- A1 --------
          /               \
    Start --  A - B1 - C - D -- End
               \      /
                - B2 -
     */
    dag.addTask("TaskA", taskA)
    dag.addTask("TaskA1", taskA1)
    dag.addTask("TaskB1", taskB1, List("TaskA")) // Depends on TaskA
    dag.addTask("TaskB2", taskB2, List("TaskA")) // Depends on TaskA
    dag.addTask("TaskC", taskC, List("TaskB1", "TaskB2")) // Depends on TaskB and TaskC
    dag.addTask("TaskD", taskD, List("TaskC", "TaskA1")) // Depends on TaskD and TaskA1

    /* Executing only this getTasks in DAG:
            -- A1 --------
          /               \
    Start --  .  - B1 - C - D -- End
               \       /
                -  .  -
     */
    val taskToExecute = Seq("TaskA1", "TaskB1", "TaskC", "TaskD")
    val executionResults = dag.execute(taskToExecute)
    val executionResultsToCompare = executionResults.filter(_.isExecuted).sortBy(_.taskName).map(_.taskOutput.get)

    executionOrder shouldEqual List(3, 2, 1, 1)
    executionResultsToCompare shouldEqual List(50, 200, 100, 150)
  }

  it should "correct handle dummy tasks" in {
    val dag = new DAG[Long]()
    executionOrder = List.empty[Int]

    val taskA = new MockDAGTask(100, 1)
    val taskB1 = new MockDAGTask(50, 2)
    val taskB2 = new MockDAGTask(250, 2)
    val taskB3 = new MockDAGTask(350, 2)
    val taskC1 = new MockDAGTask(50, 3)
    val taskC2 = new MockDAGTask(150, 3)
    val taskC3 = new MockDAGTask(300, 3)

    /* DAG:
                - B1 -       - D1 -
               /      \     /      \
    Start --  A - B2 - Dummy - D2 -- End
               \       /   \       /
                - B3 -       - D3 -
     */
    dag.addTask("TaskA", taskA)
    dag.addTask("TaskB1", taskB1, List("TaskA"))
    dag.addTask("TaskB2", taskB2, List("TaskA"))
    dag.addTask("TaskB3", taskB3, List("TaskA"))
    dag.addDummyTask("Dummy", List("TaskB1", "TaskB2", "TaskB3"))
    dag.addTask("TaskC1", taskC1, List("Dummy"))
    dag.addTask("TaskC2", taskC2, List("Dummy"))
    dag.addTask("TaskC3", taskC3, List("Dummy"))

    dag.execute()

    executionOrder shouldEqual List(3, 3, 3, 2, 2, 2, 1)
  }
}
