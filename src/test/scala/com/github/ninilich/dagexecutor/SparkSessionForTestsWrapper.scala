package com.github.ninilich.dagexecutor

import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

trait SparkSessionForTestsWrapper extends BeforeAndAfterAll {
  self: AnyFunSuite =>

  // Lazy initialization to create the SparkSession only once
  lazy val spark: SparkSession = SparkSession.builder
    .appName(s"test-app-${(new scala.util.Random).nextInt(1000000)}")
    .master("local[*]") // Set the master URL as needed
    .config("spark.ui.enabled", "false") // Disable Spark UI for testing
    .getOrCreate()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    spark.sparkContext.setLogLevel("ERROR")
  }

  override protected def afterAll(): Unit = {
    try {
      if (!spark.sparkContext.isStopped) {
        spark.stop()
      }
    } finally {
      super.afterAll()
    }
  }
}
