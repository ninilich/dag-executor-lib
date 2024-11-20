package org.ninilich.dagexecutor

/** Represents the result of a task execution.
  *
  * @param taskName
  *   The name of the task.
  * @param isExecuted
  *   Boolean flag, which indicates if the task was executed.
  * @param taskDurationSec
  *   The duration of the task execution in seconds.
  * @param taskOutput
  *   The output of the task, which can be of any type `T`.
  *
  * @tparam T
  *   The type of the output produced by the task.
  */
case class TaskExecutionResult[T](
  taskName: String,
  isExecuted: Boolean,
  taskDurationSec: Double,
  taskOutput: Option[T]
)
