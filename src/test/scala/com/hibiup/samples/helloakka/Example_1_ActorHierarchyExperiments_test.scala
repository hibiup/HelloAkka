package com.hibiup.samples.helloakka

import org.scalatest._

class Example_1_ActorHierarchyExperiments_test extends FlatSpec {
    "A Akka actor hierarchy experiment" should "" in {
        import com.hibiup.samples.helloakka.Example_1_ActorHierarchyExperiment._
        ActorHierarchyExperiment()
    }

    "Another Akka actor hierarchy experiment" should "" in {
        import com.hibiup.samples.helloakka.Exmple_1_ActorHierarchyExperiment2._
        ActorHierarchyExperiment()
    }
}
