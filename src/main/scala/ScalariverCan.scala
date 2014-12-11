/* Copyright (c) 2014 John Ferrolino

The MIT license

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished
to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/
package org.scalariver

import akka.actor.{ ActorSystem, Props }
import akka.io.IO
import spray.can.Http

object Boot extends App {

  implicit val river = ActorSystem("Scalariver")
  val handler = river.actorOf(ScalariverHandler.props, name = "scalariver")

  import com.typesafe.config._
  import util.Properties
  val serverPort = Properties.envOrElse("PORT", "8080").toInt
  val conf = ConfigFactory.load()
  val server = conf.getString("interface")

  IO(Http) ! Http.Bind(handler, interface = server, port = serverPort)
}

import akka.actor.{ Actor, ActorLogging, ActorRefFactory }
import spray.http._
import scala.concurrent.duration._
import akka.util.Timeout
import scala.util.{ Try, Success, Failure }

/**
 * Handler Actor registered to spray-can for formatting services.
 */

object ScalariverHandler {
  def props: Props = Props[ScalariverHandler]
}

class ScalariverHandler extends Actor
  with FormattingService
  with StaticContentService
  with ActorLogging {
  implicit val timeout: Timeout = 1.second
  def actorRefFactory = context
  def receive = runRoute(formatRoute ~ staticRoute)
}

import spray.routing.HttpService

trait StaticContentService extends HttpService {
  def staticRoute = path("") {
    getFromResource("index.html")
  } ~ getFromResourceDirectory("")
}

import scalariform.formatter.preferences._
import scalariform.formatter.ScalaFormatter

/**
 * Companion object of Formatting Service
 */
object FormattingService {
  val SOURCE_FIELD = "source"
  val SCALA_VERSION = "scalaVersion"
  val INDENT_LEVEL = "initialIndentLevel"

  def formatPreferences(implicit params: Map[String, String]) =
    AllPreferences.preferencesByKey map {
      case (key, descriptor) ⇒ {
        val setting = descriptor match {
          case desc: BooleanPreferenceDescriptor ⇒
            Some(if (params.get(key).isDefined) "true" else "false")
          case _ ⇒ params get key
        }
        val parsed = setting flatMap { v ⇒
          descriptor.preferenceType.parseValue(v).right.toOption
        } getOrElse descriptor.defaultValue
        descriptor -> parsed
      }
    }
}
trait FormattingService extends HttpService {
  implicit def executionContext = actorRefFactory.dispatcher
  import FormattingService._

  def formatRoute =
    path("") {
      post {
        entity(as[FormData]) { formData ⇒
          implicit val allParams = formData.fields.toMap
          val source = allParams get SOURCE_FIELD
          val version = allParams getOrElse (SCALA_VERSION, "2.10")
          val Some(indentLevel: Int) = Some((allParams getOrElse (INDENT_LEVEL, "0")) toInt)
          lazy val preferences = new FormattingPreferences(formatPreferences.toMap)
          complete {
            Try(ScalaFormatter.format(
              source = source.get,
              scalaVersion = version,
              formattingPreferences = preferences,
              initialIndentLevel = indentLevel))
          }
        }
      }
    }
}
