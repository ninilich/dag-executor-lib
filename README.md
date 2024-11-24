# DAG Executor Library

## Overview

The `dag-executor` library provides a simple implementation of a Directed Acyclic Graph (DAG) for 
managing and executing tasks in a flexible and efficient manner. This library allows users to define tasks with dependencies, execute them in the correct order, and handle any exceptions that may arise during execution.

## Use case
Sometimes there is a need to execute interrelated tasks <u>within a single application</u>, where these tasks can be partially 
parallelized—meaning they can be "assembled" into a DAG of interconnected tasks for more efficient resource utilization 
and faster overall application performance. 

E.g. 
- In event-driven systems a DAG can be used to determine the order 
of event processing. For instance, event A may trigger the processing of event B and C, which in turn initiates event D.
```
            - B -      
          /       \   
     A --          -- D -- End
          \       /   
            - C -      
```
- IN data processing, consider an application that loads 3 files from an external source and then, based on the data from all 5 files, 
calculates 2 data marts, the data from which is then aggregated into a final unified report. In this example, we essentially have a DAG:
(loading 5 files in parallel) → (calculating 2 data marts in parallel) → (calculating the final report).
```
            - file1 -       - report1 -
          /           \   /             \   
 Start --   - file2 -  ---                -- aggregation -- End
          \           /   \             /
            - file3 -       - report2 -
```

## Features

- **Task Management**: Add tasks with specified dependencies.
- **Flexibility**: Execute only specific tasks of DAG keeping all dependencies.
- **Error Handling**: Built-in error handling for task execution failures. Propagates the original (initial) exception upwards for easier analysis.
- **DAG Structure printing**: Prints the info about structure of the DAG for better understanding and debugging.
- **Implicit conversion**: Implicit conversion of the result of the task: `T -> Option[T]` and `Unit ->  None` for clearer code.


## Limitations

- all tasks should return the results with the same type `T`, `Union` or ```None```.


## Installation

To use the `dag-executor` library, include it in your project dependencies. If you are using SBT, add the following line to your `build.sbt`:

```scala
resolvers += "GitHub Packages" at "https://maven.pkg.github.com/ninilich/dag-executor"

libraryDependencies += "com.github.ninilich" %% "dag-executor_2.12" % version // check the latest version is "0.1.0"
```


## Usage

> It is recommended to configure logger (if you need it) using  ```logback.xml``` file, similar to the setup in this repository - 
> configuring the logger to include the Mapped Diagnostic Context (MDC) with key `DagTaskName` (default value, could be redefined)
> during instantiating DAG) will help with tracing and debugging the task execution flow more effectively. E.g.
> ```xml
> <encoder>
>    <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level[%X{DagTaskName}] [%F:%L] %msg%n</pattern>
> </encoder>
> ```

**Full example**

```scala
import com.github.ninilich.dagexecutor.{DAG, RunnableDAGTask, TaskExecutionResult}
import scala.concurrent.duration.Duration

// Define your tasks
val task1 = new RunnableDAGTask[String] {
  override def run(): Option[String] = {
    // using implicit conversion Unit -> None
    println("Some text")
  }
}

val task2 = new RunnableDAGTask[String] {
  override def run(): Option[String] = {
    // using implicit conversion String -> Option[String]
    "Some text"
  }
}

val task3 = new RunnableDAGTask[String] {
  override def run(): Option[String] = {
    Some("Another output text")
  }
}

// Create a DAG instance
val dag = new DAG[String](maxThreads = 4, awaitTime = Duration(10, "seconds"))

// Add tasks with dependencies
dag.addTask("Task1", task1)
dag.addTask("Task2", task2, List("Task1"))
dag.addTask("Task3", task3, List("Task1"))

/* DAG:
                - t2 -
               /       \
     Start -- t1        -- End
               \       /
                - t3 -
 */

// Execute the DAG
val results = dag.execute()

// Print the results
results.foreach { case TaskExecutionResult(name, isExecuted, duration, output) =>
  if (isExecuted) {
    println(s"Executed $name in $duration seconds with result: $output")
  } else {
    println(s"Task $name was skipped")
  }
}

// Print the DAG tasks and their dependencies
println(dag.getTasks)

```
For more examples - see [src/main/scala/com/ninilich/dagexecutor/examples](src/main/scala/com/github/ninilich/dagexecutor/examples)


## **API Reference**

### **Constructor**
```scala
def this(maxThreads: Int = 0, awaitTime: Duration = Duration.Inf, mdcKey: String = "DagTaskName")
```
- **`maxThreads`** *(Int)*: Maximum threads for concurrent task execution.
    - `0` uses the global execution context.
    - Values > `0` specify a fixed thread pool size.
- **`awaitTime`** *(Duration)*: Maximum time to wait for task execution. Default: `Duration.Inf` (no timeout).
- **`DagTaskName`** *(String)*: key for Mapped Diagnostic Context (MDC). Default: `DagTaskName`

### **Methods**
#### **addTask**
Adds a task to the DAG.
```scala
def addTask(name: String, task: RunnableDAGTask[T], dependencyNames: List[String] = List.empty): Node
```
- **Parameters**:
    - **`name`** *(String)*: Unique task name.
    - **`task`** *(RunnableDAGTask[T],)*: The task to execute.
    - **`dependencyNames`** *(List[String])*: Names of tasks this task depends on.

- **Returns**:
    - `Node`: The created node representing the task.

- **Throws**:
    - `Exception`: If a dependency cannot be found.

#### **addDummyTask**
Adds a dummy task. Could be used to simplify build of DAG. E.g. in this example we could just declare that `Dummy` depends on `B1, B2, B3` 
and `D1, D2, D2` are dependent on `Dummy` instead of declaration dependencies on `B1, B2, B3` for every `D1, D2, D2`.
```
                - B1 -       - D1 -
               /      \     /      \
     Start -  A - B2 - Dummy - D2 -- End
               \       /   \       /
                - B3 -       - D3 -

```
```scala
def addDummyTask(name: String, dependencyNames: List[String] = List.empty): Node
```
- **Parameters**:
    - **`name`** *(String)*: Unique task name.
    - **`dependencyNames`** *(List[String])*: Names of tasks this task depends on.
- **Returns**:
    - `Node`: The created node representing the task.
- **Throws**:
    - `Exception`: If a dependency cannot be found.

#### **execute**
Executes tasks in the DAG, respecting dependencies.
```scala
def execute(tasks: Seq[String] = Seq.empty): List[TaskExecutionResult[T]]
```
- **Parameters**:
    - **`tasks`** *(Seq[String])*: Names of tasks to execute. If empty, all tasks are executed.
- **Returns**:
    - `List[TaskExecutionResult[T]]`: Results of each executed task (name, status, duration, and output).
- **Throws**:
    - `Exception`: If execution fails or a task encounters an error.

#### **getTasks**
Retrieves the tasks and their dependencies.
```scala
def getTasks: Map[String, Seq[String]]
```
- **Returns**:
    - `Map[String, Seq[String]]`: A mapping of task names to their dependencies..

### **Task Result Structure**

The `TaskExecutionResult` class contains the following information:

- **`name`** *(String)*: Task name.
- **`success`** *(Boolean)*: Whether the task was executed.
- **`duration`** *(Long)*: Execution time in seconds.
- **`output`** *(Option[T])*: The task's output, if any.