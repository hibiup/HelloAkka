package com.hibiup.samples

package object cluster {
    // For Http and cluster testing
    case class Message(msg:String)
    case class Result(msg:String)

    case object AccountServiceRegistration

    // For Twirl testing
    case class Foo(bar:String)
}
