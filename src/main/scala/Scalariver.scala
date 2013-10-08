package com.github.ornicar.scalariver

import scala.util.{ Try, Success, Failure }
import tiscaf._

object ScalariverServer extends HServer with App {

  def port = args.toIndexedSeq lift 0 flatMap { p =>
    Try(p.toInt).toOption
  } getOrElse 8098

  val apps = Seq(ScalariformApp, StaticApp)
  val ports = Set(port)
  override protected val name = "scalariver"
  override protected val maxPostDataLength = 1024 * 8
  // do not start the stop thread
  override protected def startStopListener {}

  start
  println("press enter to stop...")
  Console.readLine
  stop
}

/** The application that serves the pages */
object ScalariformApp extends HApp {

  override def keepAlive = false
  override def gzip = false
  override def chunked = false
  override def tracking = HTracking.NotAllowed

  def resolve(req: HReqData): Option[HLet] =
    if (req.method == HReqType.PostData) Some(ScalariformLet) else None
}

/** Serves the current server time */
object ScalariformLet extends HSimpleLet {

  def act(talk: HTalk) {
    val f = new Format(talk.req) 
    f.apply match {
      case Success(x) ⇒ {
        val bytes = x getBytes "UTF-8"
        talk
        .setContentType("text/plain; charset=UTF-8")
        .setCharacterEncoding("UTF-8")
        .setContentLength(bytes.size)
        .setStatus(HStatus.OK)
        .write(bytes)
      }
      case Failure(e) ⇒ {
        val out = if (f.forceOutput) f.source else e.getMessage
        talk
        .setStatus(HStatus.BadRequest)
        .setContentLength(out.length)
        .write(out)
      }
    }
  }
}

object StaticApp extends HApp {

  override def buffered = true // ResourceLet needs buffered or chunked be set

  def resolve(req: HReqData) = Some(StaticLet) // generates 404 if resource not found
}

object StaticLet extends let.ResourceLet {
  protected def dirRoot = ""
  override protected def uriRoot = ""
  override protected def indexes = List("index.html")
}
