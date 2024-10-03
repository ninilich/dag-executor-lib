package com.github.ninilich.dagexecutor

import scala.language.implicitConversions

/** Trait representing a task in a Directed Acyclic Graph (DAG). Each task defines a specific unit of work that can be
  * executed within the DAG. The execution order of getTasks is determined by the dependencies within the graph.
  */
trait Runnable[T] {

  /** Executes the task's logic. This method is expected to be implemented by concrete task classes. It should contain
    * the code that performs the task's unit of work.
    */
  def run(): Option[T]

  /** Implicit conversion for Unit to None */
  implicit def unitToOption(unit: Unit): Option[T] = None

  /** Implicit conversion from T to Option[T]. Wraps the value in Some if it's not null, otherwise returns None
    */
  implicit def toOption[V](value: V): Option[V] = Some(value)

}
