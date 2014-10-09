/*
 * Licensed to STRATIO (C) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  The STRATIO (C) licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.stratio.meta2.server.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.stratio.meta.common.exceptions.ExecutionException
import com.stratio.meta.common.executionplan._
import com.stratio.meta.common.result._
import com.stratio.meta.communication.{ACK, ConnectToConnector, DisconnectFromConnector}
import com.stratio.meta2.common.data.{ConnectorName, Status}
import com.stratio.meta2.core.coordinator.Coordinator
import com.stratio.meta2.core.execution.{ExecutionInfo, ExecutionManager}
import com.stratio.meta2.core.metadata.MetadataManager
import com.stratio.meta2.core.query.PlannedQuery

object CoordinatorActor {
  def props(connectorMgr: ActorRef, coordinator: Coordinator): Props = Props(new CoordinatorActor(connectorMgr, coordinator))
}

class CoordinatorActor(connectorMgr: ActorRef, coordinator: Coordinator) extends Actor with ActorLogging {
  log.info("Lifting coordinator actor")

  /**
   * Queries in progress.
   */
  //TODO Move this to infinispan
  //val inProgress: scala.collection.mutable.Map[String, ExecutionWorkflow] = scala.collection.mutable.Map()
  //val inProgressSender: scala.collection.mutable.Map[String, ActorRef] = scala.collection.mutable.Map()

  /**
   * Queries that trigger a persist operation once the result is returned.
   */
  //TODO Move this to infinispan
  //val persistOnSuccess: scala.collection.mutable.Map[String, MetadataWorkflow] = scala.collection.mutable.Map()

  //TODO Move this to infinispan
  //val pendingQueries: scala.collection.mutable.Map[FirstLevelName, String] = scala.collection.mutable.Map()

  def receive = {

      case plannedQuery: PlannedQuery => {
      val workflow = plannedQuery.getExecutionWorkflow()
      log.info("\n\n\n\n>>>>>> TRACE: Workflow from "+workflow.getActorRef)

      workflow match {
        case workflow: MetadataWorkflow => {
          log.info("\n\n\n\n>>>>>> TRACE: MetadataWorkflow from "+workflow.getActorRef)
          val executionInfo = new ExecutionInfo
          executionInfo.setSender(sender)
          val queryId = plannedQuery.getQueryId
          executionInfo.setWorkflow(workflow)
          if(workflow.getActorRef != null){
            executionInfo.setQueryStatus(QueryStatus.IN_PROGRESS)
            executionInfo.setPersistOnSuccess(true)
            ExecutionManager.MANAGER.createEntry(queryId, executionInfo)
            workflow.getActorRef.asInstanceOf[ActorRef] ! workflow.createMetadataOperationMessage(queryId)
            sender ! ACK(queryId, QueryStatus.EXECUTED)
          } else {
            executionInfo.setQueryStatus(QueryStatus.PLANNED)
            ExecutionManager.MANAGER.createEntry(workflow.getCatalogMetadata.getName.toString, queryId)
            ExecutionManager.MANAGER.createEntry(queryId, executionInfo)
          }
        }

        case workflow: StorageWorkflow => {
          val queryId = plannedQuery.getQueryId
          val executionInfo = new ExecutionInfo
          executionInfo.setSender(sender)
          executionInfo.setWorkflow(workflow)
          executionInfo.setQueryStatus(QueryStatus.IN_PROGRESS)
          ExecutionManager.MANAGER.createEntry(queryId, executionInfo)
          workflow.getActorRef.asInstanceOf[ActorRef] ! workflow.getStorageOperation(queryId)
        }

        case workflow: ManagementWorkflow => {
          log.info(">>>>>> TRACE: ManagementWorkflow ")
          val requestSender = sender
          val queryId = plannedQuery.getQueryId
          requestSender ! coordinator.executeManagementOperation(workflow.createManagementOperationMessage(queryId))
        }

        case workflow: QueryWorkflow => {
          val queryId = plannedQuery.getQueryId
          val executionInfo = new ExecutionInfo
          executionInfo.setSender(sender)
          executionInfo.setWorkflow(workflow)
          executionInfo.setQueryStatus(QueryStatus.IN_PROGRESS)
          if(ResultType.RESULTS.equals(workflow.getResultType)){
            ExecutionManager.MANAGER.createEntry(queryId, executionInfo)
            workflow.getActorRef.asInstanceOf[ActorRef] ! workflow.getWorkflow
          }else if(ResultType.TRIGGER_EXECUTION.equals(workflow.getResultType)){
            //TODO Trigger next step execution.
            throw new UnsupportedOperationException("Trigger execution not supported")
          }
        }
        case _ =>{
          println("non recognized workflow")
        }
      }
    }

    case result: Result => {
      val queryId = result.getQueryId
      println("receiving result from "+sender+"; queryId="+queryId)
      val executionInfo = ExecutionManager.MANAGER.getValue(queryId)
      val clientActor = executionInfo.asInstanceOf[ExecutionInfo].getSender
      if(executionInfo.asInstanceOf[ExecutionInfo].isPersistOnSuccess){
        coordinator.persist(executionInfo.asInstanceOf[ExecutionInfo].getWorkflow.asInstanceOf[MetadataWorkflow ])
        ExecutionManager.MANAGER.deleteEntry(queryId)
      }
      clientActor.asInstanceOf[ActorRef] ! result
    }

    case ctc: ConnectToConnector =>
      MetadataManager.MANAGER.setConnectorStatus(new ConnectorName(ctc.msg),Status.ONLINE)
      log.info("connected to connector ")

    case ctc: DisconnectFromConnector =>
      MetadataManager.MANAGER.setConnectorStatus(new ConnectorName(ctc.msg),Status.OFFLINE)
      log.info("disconnected from connector ")

    case _ => {
      //sender ! Result.createUnsupportedOperationErrorResult("Not recognized object")
      sender ! new ExecutionException("Non recogniced workflow")
    }

  }

}
