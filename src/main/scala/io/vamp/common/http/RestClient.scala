package io.vamp.common.http

import com.typesafe.scalalogging.Logger
import dispatch._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._

object RestClient {

  private val logger = Logger(LoggerFactory.getLogger(RestClient.getClass))

  object Method extends Enumeration {
    val HEAD, GET, POST, PUT, DELETE, PATCH, TRACE, OPTIONS = Value
  }

  val acceptEncodingIdentity: (String, String) = "accept-encoding" -> "identity"

  val jsonHeaders: List[(String, String)] = List("Accept" -> "application/json", "Content-Type" -> "application/json")

  def get[A](url: String, headers: List[(String, String)] = jsonHeaders)
            (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](Method.GET, url, None, headers)
  }

  def post[A](url: String, body: Any, headers: List[(String, String)] = jsonHeaders)
             (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](Method.POST, url, body, headers)
  }

  def put[A](url: String, body: Any, headers: List[(String, String)] = jsonHeaders)
            (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    http[A](Method.PUT, url, body, headers)
  }

  def delete(url: String, headers: List[(String, String)] = jsonHeaders)(implicit executor: ExecutionContext) = {
    http(Method.DELETE, url, None)
  }

  def http[A](method: Method.Value, url: String, body: Any, headers: List[(String, String)] = jsonHeaders)
             (implicit executor: ExecutionContext, mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {

    val requestWithUrl = dispatch.url(url).setMethod(method.toString)
    val requestWithHeaders = headers.foldLeft(requestWithUrl)((http, header) => http.setHeader(header._1, header._2))
    val requestWithBody = bodyAsString(body) match {
      case Some(some) =>
        logger.trace(s"req [${method.toString} $url] - $some")
        requestWithHeaders.setBody(some)
      case None =>
        logger.trace(s"req [${method.toString} $url]")
        requestWithHeaders
    }

    if (classTag[A].runtimeClass == classOf[Nothing]) {
      Http(requestWithBody OK as.String).map { string =>
        logger.trace(s"rsp [${method.toString} $url] - $string")
        string.asInstanceOf[A]
      }
    } else if (classTag[A].runtimeClass == classOf[String]) {
      Http(requestWithBody OK as.String).map { string =>
        logger.trace(s"rsp [${method.toString} $url] - $string")
        string.asInstanceOf[A]
      }
    } else {
      Http(requestWithBody OK dispatch.as.json4s.Json).map { json =>
        logger.trace(s"rsp [${method.toString} $url] - ${compact(render(json))}")
        json.extract[A](formats, mf)
      }
    }
  }

  private def bodyAsString(body: Any)(implicit formats: Formats): Option[String] = body match {
    case string: String => Some(string)
    case Some(string: String) => Some(string)
    case Some(some: AnyRef) => Some(write(some))
    case any: AnyRef if any != null && any != None => Some(write(any))
    case any if any != null && any != None => Some(any.toString)
    case _ => None
  }
}
