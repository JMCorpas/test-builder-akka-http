package com


import com.typesafe.config.{Config, ConfigFactory}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, concat, get, pathEnd, pathPrefix}
import akka.http.scaladsl.server.Route

import scala.util.Failure
import scala.util.Success
object Main {
  //#start-http-server
  private def startHttpServer(routes: Route, config: Config, system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    implicit val classicSystem: akka.actor.ActorSystem = system.toClassic
    import system.executionContext

    val futureBinding = Http().bindAndHandle(routes,
      config.getString("http.service.bind-to"),
      config.getInt("http.service.port"))
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {

    //#server-bootstrapping
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val config = ConfigFactory.load()

      val routes: Route =
        pathPrefix("hello") {
          concat(
            pathEnd {
              concat(
                get {
                  complete("Hello Akka-http")
                }
              )
            })
        }
      startHttpServer(routes, config, context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "HelloAkkaHttpServer")
    //#server-bootstrapping


  }

}
