package com.github.everpeace.kafka

import com.github.everpeace.kafka.reassign_optimizer.util.Tabulator
import kafka.common.TopicAndPartition

package object reassign_optimizer {

  type ReplicaAssignment = Map[(String, Int), List[Int]]

  import scalaz.Isomorphism.<=>

  implicit val isoTopicAndPartitionAndTuple = new (TopicAndPartition <=> (String, Int)) {
    override def to: (TopicAndPartition) => (String, Int) = tp => tp.topic -> tp.partition

    override def from: ((String, Int)) => TopicAndPartition = tp => TopicAndPartition(tp._1, tp._2)
  }

  implicit def tpToTopicAndPartition(tp: (String, Int)): TopicAndPartition = isoTopicAndPartitionAndTuple.from(tp)

  implicit def fromTopicAndPartitionToTuple(tp: TopicAndPartition): (String, Int) = isoTopicAndPartitionAndTuple.to(tp)

  implicit class ReplicaAssignmentOps(assignment: ReplicaAssignment) {

    val tpbPairs: List[(String, Int, Int)] = for {
      tp <- assignment.keys.toList
      replica <- assignment(tp)
    } yield (tp._1, tp._2, replica)

    def show(implicit problem: ReassignOptimizationProblem): String = {
      val weights = problem.partitionWeights
      val sortedBrokers = problem.brokersOnProblem.toList.sorted
      val brokersRow = List("broker") ++ sortedBrokers.map(_.toString) ++ List("")

      var existsNewLeader = false
      val assignmentRows = for {topicPartition <- assignment.keys.toList.sorted} yield {
        val as = sortedBrokers.map { broker =>
          if (assignment(topicPartition).contains(broker)) {
            if (assignment(topicPartition).head == broker && problem.topicPartitionInfos.assignment(topicPartition).head == broker) {
              s"⚐${weights(topicPartition)}"
            }
            else if (assignment(topicPartition).head == broker) {
              existsNewLeader = true
              s"⚑${weights(topicPartition)}"
            }
            else {
              s" ${weights(topicPartition)}"
            }
          } else {
            " 0"
          }
        }
        List(s"[${topicPartition._1}, ${topicPartition._2}]") ++ as ++ List(s"(RF = ${as.filterNot(_ == " 0").length})")
      }

      val brokerWeightsRow = List("weight") ++ {
        val w = brokerWeights
        sortedBrokers.map { b =>
          w(b).toString
        }
      } ++ List("")

      val legends = if (existsNewLeader)
        List('⚐' -> "leader partition", '⚑' -> "new leader partition")
      else
        List('⚐' -> "leader partition")

      Tabulator.format(
        List(brokersRow) ++ assignmentRows ++ List(brokerWeightsRow),
        legends
      )
    }

    def brokerWeights(implicit problem: ReassignOptimizationProblem): Map[Int, Int] = (for {
      b <- problem.brokersOnProblem
    } yield {
      b -> (for {
        topicPartition <- assignment.keys.toList
      } yield {
        if (assignment(topicPartition).contains(b)) problem.partitionWeights(topicPartition)
        else 0
      }).sum
    }).toMap

    def totalReplicaWeight(implicit problem: ReassignOptimizationProblem): Int = tpbPairs.map(tpb => problem.partitionWeights(tpb._1 -> tpb._2)).sum
  }

  implicit class TopicPartitionInfosOps(tpis: List[TopicPartitionInfo]) {
    val assignment: ReplicaAssignment = tpis.map(tpi => (tpi.tp.topic, tpi.tp.partition) -> tpi.replicas).toMap
    val tpbPairs: List[(String, Int, Int)] = assignment.tpbPairs
  }

  implicit def fromTopicPartitionInfosToReplicaAssignment(tpis: List[TopicPartitionInfo]): ReplicaAssignment = tpis.assignment

  implicit def fromTopicPartitionInfosToTpbPairs(tpis: List[TopicPartitionInfo]): List[(String, Int, Int)] = tpis.tpbPairs
}
