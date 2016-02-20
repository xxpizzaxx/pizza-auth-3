package moe.pizza.auth.webapp.util

import play.twirl.api.HtmlFormat
import spark._

object SparkWebScalaHelpers {
  /*
  implicit def stringlambda2route(l: (Request, Response) => String): Route = new Route {
    override def handle(request: Request, response: Response): String = l(request, response)
  }
  */

  implicit def twirllambda2route(l: (Request, Response) => HtmlFormat.Appendable): Route = new Route {
    override def handle(request: Request, response: Response): String = l(request, response).toString()
  }

  implicit def lambda2modelroute(l: (Request, Response) => ModelAndView): TemplateViewRoute = new TemplateViewRoute {
    override def handle(request: Request, response: Response): ModelAndView = l(request, response)
  }

  implicit def unitlambda2filter(l: (Request, Response) => Unit): Filter = new Filter {
    override def handle(request: Request, response: Response): Unit = l(request, response)
  }

  implicit def unitlambda2route(l: (Request, Response) => Unit): Route = new Route {
    override def handle(request: Request, response: Response): AnyRef = { l(request, response); "" }
  }
}
