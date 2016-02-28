package moe.pizza.auth.plugins.userfilters

import fastparse.core.Parsed.Success
import moe.pizza.auth.interfaces.UserFilter
import moe.pizza.auth.models.Pilot
import net.andimiller.iql.Ast.World
import net.andimiller.iql.Evaluator.EvaluatablePipeline
import net.andimiller.iql.{Ast, Parser}


class IqlFilter extends UserFilter {

  override def filter(users: Seq[Pilot], rule: String): Seq[Pilot] = {
    Parser.OperatorExpression.parse(rule) match {
      case Success(v, i) =>
        users.filter { p =>
          val r = v.eval(new World(p.toJsonNode, Pilot.OM.createObjectNode()))
          r match {
            case Ast.Bool(v) => v
            case _ => false
          }
        }
      case f =>
        Seq.empty[Pilot]
    }
  }
}
