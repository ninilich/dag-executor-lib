package com.github.ninilich.dagexecutor

import org.slf4j.{Logger, MDC}

import java.time.Instant
import java.util.concurrent.Executors
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/** A class representing a Directed Acyclic Graph (DAG) for managing and executing getTasks with dependencies. It
  * ensures that getTasks are executed in the correct order based on their dependencies and handles errors during
  * execution. The getTasks are executed concurrently with support for a maximum number of threads and optional
  * execution timeout.
  *
  * @tparam T
  *   The type of result produced by the getTasks in the DAG. This allows flexibility in the types of getTasks that can
  *   be handled (e.g., String, Int, custom objects).
  *
  * @param maxThreads
  *   The maximum number of threads to use for concurrent execution. A value of 0 uses the global execution context, and
  *   a value greater than 0 specifies the number of threads in a fixed pool. If no threads are needed, set this to 0
  *   (default).
  * @param awaitTime
  *   The maximum time to wait for the DAG execution to complete. If the execution takes longer, an exception will be
  *   thrown. Default is `Duration.Inf`, meaning it will wait indefinitely until completion.
  * @param logger
  *   A logger used to log task execution events.
  */
class DAG[T](maxThreads: Int = 0, awaitTime: Duration = Duration.Inf)(implicit logger: Logger) {

  private type TaskName = String

  private val nodes = mutable.ListBuffer[Node]()
  private var tasksToExecute: Seq[TaskName] = _

  /** Creates an `ExecutionContext` with a specified number of threads. If `maxThreads` is greater than 0, it creates a
    * fixed thread pool with that many threads. Otherwise, it uses the global `ExecutionContext`.
    *
    * @param maxThreads
    *   The maximum number of threads for execution.
    * @return
    *   An `ExecutionContext` with the specified number of threads or the global context.
    */
  private def createExecutionContext(maxThreads: Int = 0): ExecutionContext = {
    if (maxThreads > 0) ExecutionContext.fromExecutor(Executors.newFixedThreadPool(maxThreads))
    else ExecutionContext.global
  }

  private implicit val executionContext: ExecutionContext = createExecutionContext(maxThreads)

  /** A case class representing a node in the DAG. */
  case class Node(task: Runnable[T], name: TaskName, dependencies: List[Node] = List())

  /** A dummy task. This class is primarily used as a placeholder or for testing scenarios.
    */
  private class DummyTask extends Runnable[T] {

    override def run(): Option[T] = None
  }

  /** Adds a new task node with dependencies to the DAG.
    *
    * @param task
    *   The task to be executed.
    * @param name
    *   The unique name of the task.
    * @param dependencies
    *   A list of nodes this task depends on. The task will only run after its dependencies have completed.
    * @return
    *   The newly added node.
    */
  private def addNode(task: Runnable[T], name: TaskName, dependencies: List[Node]): Node = {
    val newNode = Node(task, name, dependencies)
    nodes += newNode
    newNode
  }

  /** Adds a task to the DAG by specifying the task name, the task itself, and its dependencies by their names.
    *
    * @param name
    *   The name of the task.
    * @param task
    *   The task to be executed.
    * @param dependencyNames
    *   A list of names of getTasks that must complete before this task can be executed.
    * @return
    *   The node representing the added task.
    * @throws Exception
    *   If any of the specified dependencies cannot be found.
    */
  def addTask(name: TaskName, task: Runnable[T], dependencyNames: List[TaskName] = List.empty): Node = {
    val dependencyNodes = dependencyNames.map { depName =>
      nodes.find(_.name == depName).getOrElse(throw new Exception(s"Dependency '$depName' not found"))
    }
    addNode(task, name, dependencyNodes)
  }

  def addDummyTask(name: TaskName, dependencyNames: List[TaskName] = List.empty): Node = {
    addTask(name, new DummyTask, dependencyNames)
  }

  /** Executes a task and returns its result along with execution time. The execution time is calculated in seconds and
    * logged.
    *
    * @param node
    *   The node (task) to be executed.
    * @return
    *   The result of the task execution, including task name, duration, and output.
    * @throws Exception
    *   If task execution fails.
    */
  private def executeTaskWithErrorHandling(node: Node): TaskExecutionResult[T] = {
    val isExecuteTask =
      (tasksToExecute.contains(node.name) || tasksToExecute.isEmpty) && !node.task.isInstanceOf[DummyTask]
    if (isExecuteTask) {
      val startTime = Instant.now() // Start timing
      try {
        MDC.put("DagTaskName", s" / ${node.name}") // Set the name of the task in MDC
        logger.info(s"Executing ${node.name}")
        val taskResult = node.task.run() // Execute the task
        val endTime = Instant.now() // End timing
        val duration = java.time.Duration.between(startTime, endTime).toSeconds
        TaskExecutionResult(node.name, true, duration, taskResult) // Return result
      } catch {
        case e: Exception =>
          throw new Exception(s"Task '${node.name}' failed", e) // Rethrow custom exception
      } finally {
        MDC.clear() // Clear MDC after task completion
      }
    } else {
      TaskExecutionResult(node.name, false, 0, None) // Return None in case of skip of task execution
    }
  }

  /** Executes all getTasks in the DAG, respecting dependencies between getTasks. Returns a list of execution results.
    * @param tasks
    *   a sequence of names of getTasks, which should be executed. By default - all getTasks will be executed.
    * @return
    *   A list of results for each executed task, including task names, execution times, and outputs.
    * @throws Exception
    *   If the DAG execution fails or if any task fails during execution.
    */
  def execute(tasks: Seq[TaskName] = Seq.empty): List[TaskExecutionResult[T]] = {
    tasksToExecute = tasks
    val results = mutable.Map[Node, Future[TaskExecutionResult[T]]]()

    def executeNode(node: Node): Future[TaskExecutionResult[T]] = {
      results.getOrElseUpdate(
        node, {
          val deps = node.dependencies.map(executeNode) // Execute dependencies first
          Future.sequence(deps).flatMap { _ =>
            Future {
              executeTaskWithErrorHandling(node)
            }
          }
        }
      )
    }

    val futureResults = Future.sequence(nodes.toList.map(executeNode)).recover { case e: Exception =>
      throw new Exception("DAG execution failed", e) // Rethrow exception to reflect overall failure
    }
    Await.result(futureResults, awaitTime)
  }

  /** Returns getTasks and its dependencies.
    *
    * @return
    *   A Map where key is TaskName and value is the Seq of their dependencies (also TaskNames).
    */
  def getTasks: Map[TaskName, Seq[TaskName]] = {
    nodes.map(node => (node.name, node.dependencies.map(_.name))).toMap
  }
}
