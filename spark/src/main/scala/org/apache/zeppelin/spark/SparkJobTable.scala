/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.spark

import java.util.concurrent.ConcurrentHashMap

import scala.collection.mutable.HashMap

import org.apache.spark.scheduler.SparkListenerJobStart

/**
 * Spark listener job keeps information for started Spark job.
 *
 */
class SparkListenerJob(jobStart: SparkListenerJobStart, uiAddress: String) extends Serializable {
  private val jobId: Int = jobStart.jobId
  private val stageIds: Array[Int] = jobStart.stageIds.toArray
  private val jobUIAddress: String = getJobURL(jobId)

  private def getJobURL(jobId: Int): String = {
    if (uiAddress == null) null else s"$uiAddress/jobs/job/?id=$jobId"
  }

  def getJobId(): Int = jobId

  def getStageIds(): Array[Int] = stageIds

  def getJobUIAddress(): String = jobUIAddress

  def getJobName(): String = s"Job $jobId"

  def getJobDescription(): String = {
    stageIds.map { stageId => s"Stage $stageId" }.mkString("\n")
  }

  override def toString(): String = {
    s"${getClass.getSimpleName}" +
      s"(jobId=$jobId, url=$jobUIAddress, stages=${stageIds.mkString("[", ", ", "]")})"
  }
}

/**
 * Table to keep information about current state of all jobs for application.
 *
 */
class SparkJobTable(private val uiAddress: String) {
  // table contains map of jobs for job group, each job map has job id as key
  private val table = new ConcurrentHashMap[String, HashMap[Int, SparkListenerJob]]()

  def addJob(jobGroup: String, jobStart: SparkListenerJobStart): Unit = {
    val listenerJob = new SparkListenerJob(jobStart, uiAddress)
    if (!table.containsKey(jobGroup)) {
      // create new map and add job
      table.put(jobGroup, new HashMap[Int, SparkListenerJob]())
    }
    val map = table.get(jobGroup)
    map.put(listenerJob.getJobId(), listenerJob)
  }

  def reset(jobGroup: String): Unit = {
    if (table.containsKey(jobGroup)) {
      table.get(jobGroup).clear()
    }
  }

  /** Reset entire table */
  def reset(): Unit = {
    table.clear()
  }

  /** Return list of jobs for job group, if no jobs found returns empty array */
  def getJobs(jobGroup: String): Array[SparkListenerJob] = {
    val jobs = table.get(jobGroup)
    if (jobs == null) Array.empty else jobs.values.toArray.sortBy(_.getJobId())
  }
}
